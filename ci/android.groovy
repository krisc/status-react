nix = load 'ci/nix.groovy'
utils = load 'ci/utils.groovy'

def bundle() {
  /* we use the method because parameter build type does not take e2e into account */
  def btype = utils.getBuildType()
  /* Disable Gradle Daemon https://stackoverflow.com/questions/38710327/jenkins-builds-fail-using-the-gradle-daemon */
  def gradleOpt = "-PbuildUrl='${currentBuild.absoluteUrl}' --console plain "
  /* we don't need x86 for any builds except e2e */
  env.ANDROID_ABI_INCLUDE="armeabi-v7a;arm64-v8a"

  /* some builds tyes require different architectures */
  switch (btype) {
    case 'e2e':
      env.ANDROID_ABI_INCLUDE="x86" /* e2e builds are used with simulators */
    case 'release':
      env.ANDROID_ABI_SPLIT="true"
      gradleOpt += "-PreleaseVersion='${utils.getVersion()}'"
  }

  /* credentials necessary to open the keystore and sign the APK */
  withCredentials([
    string(
      credentialsId: 'android-keystore-pass',
      variable: 'STATUS_RELEASE_STORE_PASSWORD'
    ),
    usernamePassword(
      credentialsId: 'android-keystore-key-pass',
      usernameVariable: 'STATUS_RELEASE_KEY_ALIAS',
      passwordVariable: 'STATUS_RELEASE_KEY_PASSWORD'
    )
  ]) {
    /* Nix target which produces the final APKs */
    nix.build(
      attr: 'targets.mobile.android.release',
      args: [
        'gradle-opts': gradleOpt,
        'build-number': utils.readBuildNumber(),
        'build-type': btype,
      ],
      safeEnv: [
        'STATUS_RELEASE_KEY_ALIAS',
        'STATUS_RELEASE_STORE_PASSWORD',
        'STATUS_RELEASE_KEY_PASSWORD',
      ],
      keep: [
        'ANDROID_ABI_SPLIT',
        'ANDROID_ABI_INCLUDE',
        'STATUS_RELEASE_STORE_FILE',
      ],
      sbox: [
        env.STATUS_RELEASE_STORE_FILE,
      ],
      link: false
    )
  }
  /* necessary for Fastlane */
  env.APK_PATHS = renameAPKs()
  return pkg
}

/**
 * We need more informative filenames for all builds.
 * For more details on the format see utils.pkgFilename().
 **/
def renameAPKs() {
  /* find all APK files */
  def apkGlob = 'result/*.apk'
  def found = findFiles(glob: apkGlob)
  if (found.size() == 0) {
    error("APKs not found via glob: ${apkGlob}")
  }
  def renamed = []
  def pattern = /app-([^-]*)-release.apk/
  println "found: ${found}"
  /* rename each for upload & archiving */
  for (apk in found) {
    println "apk: ${apk}"
    /* non-release builds make universal APKs */
    def arch = 'universal'
    /* extract architecture from filename */
    def matches = (apk.name =~ pattern)
    println "matches: ${matches}"
    if (matches.size() > 0) {
        arch = matches[0][1]
    }
    println "Arch: ${arch}"
    println "Arch: ${arch.getClass()}"
    def pkg = utils.pkgFilename(btype, 'apk', arch)
    println "Pkg: ${pkg}"
    def newApk = "result/${pkg}"
    println "newApk: ${newApk}"
    renamed += newApk
    println "renamed: ${renamed}"
    println "Arch: ${apk.path.getClass()}"
    println "Arch: ${newApk.getClass()}"
    println "cp ${apk.path} ${newApk}"
    sh "cp ${apk.path} ${newApk}"
    println "END"
  }
  println "Renamed: ${renamed}"
  return renamed
}

def uploadToPlayStore(type = 'nightly') {
  withCredentials([
    string(credentialsId: "SUPPLY_JSON_KEY_DATA", variable: 'GOOGLE_PLAY_JSON_KEY'),
  ]) {
    nix.shell(
      "fastlane android ${type}",
      attr: 'targets.mobile.fastlane.shell',
      keep: ['FASTLANE_DISABLE_COLORS', 'GOOGLE_PLAY_JSON_KEY']
    )
  }
}

def uploadToSauceLabs() {
  def changeId = utils.changeId()
  if (changeId != null) {
    env.SAUCE_LABS_NAME = "${changeId}.apk"
  } else {
    def pkg = utils.pkgFilename(utils.getBuildType(), 'apk')
    env.SAUCE_LABS_NAME = "${pkg}"
  }
  withCredentials([
    usernamePassword(
      credentialsId:  'sauce-labs-api',
      usernameVariable: 'SAUCE_USERNAME',
      passwordVariable: 'SAUCE_ACCESS_KEY'
    ),
  ]) {
    nix.shell(
      'fastlane android saucelabs',
      attr: 'targets.mobile.fastlane.shell',
      keep: [
        'FASTLANE_DISABLE_COLORS', 'APK_PATH',
        'SAUCE_ACCESS_KEY', 'SAUCE_USERNAME', 'SAUCE_LABS_NAME'
      ]
    )
  }
  return env.SAUCE_LABS_NAME
}

def uploadToDiawi() {
  withCredentials([
    string(credentialsId: 'diawi-token', variable: 'DIAWI_TOKEN'),
  ]) {
    nix.shell(
      'fastlane android upload_diawi',
      attr: 'targets.mobile.fastlane.shell',
      keep: ['FASTLANE_DISABLE_COLORS', 'APK_PATH', 'DIAWI_TOKEN']
    )
  }
  diawiUrl = readFile "${env.WORKSPACE}/fastlane/diawi.out"
  return diawiUrl
}

def coverage() {
  withCredentials([
    string(credentialsId: 'coveralls-status-react-token', variable: 'COVERALLS_REPO_TOKEN'),
  ]) {
    nix.shell(
      'make coverage',
      keep: ['COVERALLS_REPO_TOKEN', 'COVERALLS_SERVICE_NAME', 'COVERALLS_SERVICE_JOB_ID']
    )
  }
}

return this
