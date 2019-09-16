(ns status-im.ui.screens.intro.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [status-im.ui.components.react :as react]
            [re-frame.core :as re-frame]
            [status-im.react-native.resources :as resources]
            [status-im.privacy-policy.core :as privacy-policy]
            [status-im.utils.utils :as utils]
            [status-im.multiaccounts.create.core :refer [step-kw-to-num]]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.utils.identicon :as identicon]
            [status-im.ui.components.radio :as radio]
            [status-im.ui.components.text-input.view :as text-input]
            [taoensso.timbre :as log]
            [status-im.utils.gfycat.core :as gfy]
            [status-im.ui.components.colors :as colors]
            [reagent.core :as r]
            [status-im.ui.components.toolbar.actions :as actions]
            [status-im.ui.components.common.common :as components.common]
            [status-im.ui.screens.intro.styles :as styles]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.utils.security :as security]
            [status-im.i18n :as i18n]
            [status-im.ui.components.status-bar.view :as status-bar]
            [status-im.constants :as constants]
            [status-im.utils.config :as config]
            [status-im.utils.platform :as platform]))

(defn dots-selector [{:keys [on-press n selected color]}]
  [react/view {:style (styles/dot-selector n)}
   (doall
    (for [i (range n)]
      ^{:key i}
      [react/view {:style (styles/dot color (selected i))}]))])

(defn intro-viewer [slides window-width window-height]
  (let [scroll-x (r/atom 0)
        margin    32
        scroll-view-ref (atom nil)]
    (fn []
      [react/view {:style {:align-items :center
                           :flex 1
                           :justify-content :flex-end}}
       [react/scroll-view {:horizontal true
                           :paging-enabled true
                           :ref #(reset! scroll-view-ref %)
                           :shows-vertical-scroll-indicator false
                           :shows-horizontal-scroll-indicator false
                           :pinch-gesture-enabled false
                           :on-scroll #(let [x (.-nativeEvent.contentOffset.x %)]
                                         (reset! scroll-x x))
                           :style {:width window-width
                                   :margin-bottom 32}}
        (for [s slides]
          ^{:key (:title s)}
          [react/view {:style {:width window-width
                               :flex 1
                               :justify-content :flex-end
                               :padding-horizontal 32}}
           [react/view {:style {:justify-content :center
                                :margin-vertical 16}}
            [react/image {:source (:image s)
                          :resize-mode :contain
                          :style {:width (- window-width (* 2 margin))
                                  :height (- window-height 350 (* 2 margin))}}]]
           [react/i18n-text {:style styles/wizard-title :key (:title s)}]
           [react/i18n-text {:style styles/wizard-text
                             :key   (:text s)}]])]
       (let [selected (hash-set (quot (int @scroll-x) (int window-width)))]
         [dots-selector {:selected selected :n (count slides)
                         :color colors/blue}])])))

(defview intro []
  (letsubs [{window-width :width window-height :height} [:dimensions/window]]
    [react/view {:style styles/intro-view}
     [status-bar/status-bar {:flat? true}]
     [intro-viewer [{:image (:intro1 resources/ui)
                     :title :intro-title1
                     :text :intro-text1}
                    {:image (:intro2 resources/ui)
                     :title :intro-title2
                     :text :intro-text2}
                    {:image (:intro3 resources/ui)
                     :title :intro-title3
                     :text :intro-text3}] window-width window-height]
     [react/view styles/buttons-container
      [components.common/button {:button-style (assoc styles/bottom-button :margin-bottom 16)
                                 :on-press     #(re-frame/dispatch [:multiaccounts.create.ui/intro-wizard true])
                                 :label        (i18n/label :t/get-started)}]
      [components.common/button {:button-style (assoc styles/bottom-button :margin-bottom 24)
                                 :on-press    #(re-frame/dispatch [:multiaccounts.recover.ui/recover-multiaccount-button-pressed])
                                 :label       (i18n/label :t/access-key)
                                 :background? false}]
      [react/nested-text
       {:style styles/welcome-text-bottom-note}
       (i18n/label :t/intro-privacy-policy-note1)
       [{:style (assoc styles/welcome-text-bottom-note :color colors/blue)
         :on-press privacy-policy/open-privacy-policy-link!}
        (i18n/label :t/intro-privacy-policy-note2)]]]]))

(defn generate-key [view-height]
  (let [image-height 140
        image-width 154
        image-container-height (- view-height 328)]
    [react/view {:style {:margin-horizontal 80
                         :align-items :center
                         :justify-content :flex-start
                         :flex 1
                         :margin-top    (/ (- image-container-height image-height) 2)}}
     [react/image {:source (resources/get-image :sample-key)
                   :resize-mode :contain
                   :style {:width image-width :height image-height}}]]))

(defn choose-key [{:keys [multiaccounts selected-id] :as wizard-state} view-height]
  [react/scroll-view {:content-container-style {:flex 1
                                                :justify-content :flex-end
                                                ;; We have to align top multiaccount entry
                                                ;; with top key storage entry on the next screen
                                                :margin-bottom (if (< view-height 600)
                                                                 -20
                                                                 (/ view-height 12))}}
   (for [acc multiaccounts]
     (let [selected? (= (:id acc) selected-id)
           public-key (get-in acc [:derived constants/path-whisper-keyword :publicKey])]
       ^{:key public-key}
       [react/touchable-highlight
        {:on-press #(re-frame/dispatch [:intro-wizard/on-key-selected (:id acc)])}
        [react/view {:style (styles/list-item selected?)}

         [react/image {:source      {:uri (identicon/identicon public-key)}
                       :resize-mode :cover
                       :style       styles/multiaccount-image}]
         [react/view {:style {:margin-horizontal 16 :flex 1 :justify-content :space-between}}
          [react/text {:style (assoc styles/wizard-text :text-align :left
                                     :color colors/black
                                     :font-weight "500")
                       :number-of-lines 1
                       :ellipsize-mode :middle}
           (gfy/generate-gfy public-key)]
          [react/text {:style (assoc styles/wizard-text
                                     :text-align :left
                                     :font-family "monospace")}
           (utils/get-shortened-address public-key)]]
         [radio/radio selected?]]]))])

(defn storage-entry [{:keys [type icon icon-width icon-height
                             image image-selected image-width image-height
                             title desc]} selected-storage-type]
  (let [selected? (= type selected-storage-type)]
    [react/view
     [react/view {:style {:padding-top 14 :padding-bottom 4}}
      [react/text {:style (assoc styles/wizard-text :text-align :left :margin-left 16)}
       (i18n/label type)]]
     [react/touchable-highlight
      {:on-press #(re-frame/dispatch [:intro-wizard/on-key-storage-selected (if (and config/hardwallet-enabled?
                                                                                     platform/android?) type :default)])}
      [react/view (assoc (styles/list-item selected?)
                         :align-items :flex-start
                         :padding-top 16
                         :padding-bottom 12)
       (if image
         [react/image
          {:source (resources/get-image (if selected? image-selected image))
           :style  {:width image-width :height image-height}}]
         [vector-icons/icon icon {:color (if selected? colors/blue colors/gray)
                                  :width icon-width :height icon-height}])
       [react/view {:style {:margin-horizontal 16 :flex 1}}
        [react/text {:style (assoc styles/wizard-text :font-weight "500" :color colors/black :text-align :left)}
         (i18n/label title)]
        [react/view {:style {:min-height 4 :max-height 4}}]
        [react/text {:style (assoc styles/wizard-text :text-align :left)}
         (i18n/label desc)]]
       [radio/radio selected?]]]]))

(defn select-key-storage [{:keys [selected-storage-type] :as wizard-state} view-height]
  (let [storage-types [{:type        :default
                        :icon        :main-icons/mobile
                        :icon-width  24
                        :icon-height 24
                        :title       :this-device
                        :desc        :this-device-desc}
                       {:type           :advanced
                        :image          :keycard-logo-gray
                        :image-selected :keycard-logo-blue
                        :image-width    24
                        :image-height   24
                        :title          :keycard
                        :desc           :keycard-desc}]]
    [react/view {:style {:flex 1
                         :justify-content :flex-end
                         ;; We have to align top storage entry
                         ;; with top multiaccount entry on the previous screen
                         :margin-bottom (+ (- 322 226) (if (< view-height 600)
                                                         -20
                                                         (/ view-height 12)))}}
     [storage-entry (first storage-types) selected-storage-type]
     [react/view {:style {:min-height 16 :max-height 16}}]
     [storage-entry (second storage-types) selected-storage-type]]))

(defn password-container [confirm-failure? view-width]
  (let [horizontal-margin 16]
    [react/view {:style {:flex 1
                         :justify-content :space-between
                         :align-items :center :margin-horizontal horizontal-margin}}
     [react/view {:style {:justify-content :center :flex 1}}
      [react/text {:style (assoc styles/wizard-text :color colors/red
                                 :margin-bottom 16)}
       (if confirm-failure? (i18n/label :t/password_error1) " ")]

      [react/text-input {:secure-text-entry true
                         :auto-focus true
                         :text-align :center
                         :placeholder ""
                         :style (styles/password-text-input (- view-width (* 2 horizontal-margin)))
                         :on-change-text #(re-frame/dispatch [:intro-wizard/code-symbol-pressed %])}]]
     [react/text {:style (assoc styles/wizard-text :margin-bottom 16)} (i18n/label :t/password-description)]]))

(defn create-code [{:keys [confirm-failure?] :as wizard-state} view-width]
  [password-container confirm-failure? view-width])

(defn confirm-code [{:keys [confirm-failure? processing?] :as wizard-state} view-width]
  (if processing?
    [react/view {:style {:justify-content :center
                         :align-items :center}}
     [react/activity-indicator {:size      :large
                                :animating true}]
     [react/text {:style {:color      colors/gray
                          :margin-top 8}}
      (i18n/label :t/processing)]]
    [password-container confirm-failure? view-width]))

(defn enable-fingerprint [biometric-auth]
  [vector-icons/icon (if (= biometric-auth :FaceID) :main-icons/faceid :main-icons/fingerprint)
   {:container-style {:align-items :center
                      :justify-content :center}
    :width (if (= biometric-auth :FaceID) 70 76) :height (if (= biometric-auth :FaceID) 70 84)}])

(defn enable-notifications []
  [vector-icons/icon :main-icons/bell {:container-style {:align-items :center
                                                         :justify-content :center}
                                       :width 66 :height 64}])

(defn bottom-bar [{:keys [step weak-password? encrypt-with-password?
                          forward-action
                          next-button-disabled?
                          biometric-auth
                          processing?] :as wizard-state}]
  [react/view {:style {:margin-bottom (if (or (#{:choose-key :select-key-storage
                                                 :enter-phrase :recovery-success} step)
                                              (and (#{:create-code :confirm-code} step)
                                                   encrypt-with-password?))
                                        20
                                        32)
                       :align-items :center}}
   (cond (and (#{:generate-key :recovery-success} step) processing?)
         [react/activity-indicator {:animating true
                                    :size      :large}]
         (#{:generate-key :recovery-success :enable-fingerprint :enable-notifications} step)
         (let [label-kw (case step
                          :generate-key :generate-a-key
                          :recovery-success :re-encrypt-key
                          :enable-fingerprint  (if (= biometric-auth :FaceID)
                                                 :intro-wizard-title-alt6 :intro-wizard-title6)
                          :enable-notifications :intro-wizard-title7)]
           [components.common/button {:button-style styles/bottom-button
                                      :on-press     #(re-frame/dispatch
                                                      [forward-action])
                                      :label        (i18n/label label-kw)}])
         (and (#{:create-code :confirm-code} step)
              (not encrypt-with-password?))
         [components.common/button {:button-style styles/bottom-button
                                    :label (i18n/label :t/encrypt-with-password)
                                    :on-press #(re-frame/dispatch [:intro-wizard/on-encrypt-with-password-pressed])
                                    :background? false}]

         :else
         [react/view {:style styles/bottom-arrow}
          [react/view {:style {:margin-right 10}}
           [components.common/bottom-button {:on-press  #(re-frame/dispatch [forward-action])
                                             :disabled? (or processing?
                                                            (and (= step :create-code) weak-password?)
                                                            (and (= step :enter-phrase) next-button-disabled?))
                                             :forward? true}]]])
   (when (#{:enable-fingerprint :enable-notifications} step)
     [components.common/button {:button-style (assoc styles/bottom-button :margin-top 20)
                                :label (i18n/label :t/maybe-later)
                                :on-press #(re-frame/dispatch [forward-action {:skip? true}])
                                :background? false}])

   (when (or (= :generate-key step) (and processing? (= :recovery-success step)))
     [react/text {:style (assoc styles/wizard-text :margin-top 20)}
      (i18n/label (cond (= :recovery-success step)
                        :t/processing
                        processing? :t/generating-keys
                        :else :t/this-will-take-few-seconds))])])

(defn top-bar [{:keys [step encrypt-with-password? biometric-auth]}]
  (let [hide-subtitle? (or (= step :confirm-code)
                           (= step :enter-phrase)
                           (and (#{:create-code :confirm-code} step) encrypt-with-password?))
        use-faceid? (and (= step :enable-fingerprint) (= biometric-auth :FaceID))]
    [react/view {:style {:margin-top   16
                         :margin-horizontal 32}}

     [react/text {:style (cond-> styles/wizard-title
                           hide-subtitle?
                           (assoc :margin-bottom 0))}
      (i18n/label
       (cond (= step :enter-phrase)
             :t/multiaccounts-recover-enter-phrase-title
             (= step :recovery-success)
             :t/keycard-recovery-success-header
             :else (keyword (str "intro-wizard-title"
                                 (when  (or use-faceid?
                                            (and (#{:create-code :confirm-code} step) encrypt-with-password?))
                                   "-alt") (step-kw-to-num step)))))]
     (cond (#{:choose-key :select-key-storage} step)
           ; Use nested text for the "Learn more" link
           [react/nested-text {:style styles/wizard-text}
            (str (i18n/label (keyword (str "intro-wizard-text" (step-kw-to-num step)))) " ")
            [{:on-press #(re-frame/dispatch [:bottom-sheet/show-sheet :learn-more
                                             {:title (i18n/label (if (= step :choose-key) :t/about-names-title :t/about-key-storage-title))
                                              :content  (i18n/label (if (= step :choose-key) :t/about-names-content :t/about-key-storage-content))}])
              :style {:color colors/blue}}
             (i18n/label :learn-more)]]
           (not hide-subtitle?)
           [react/text {:style styles/wizard-text}
            (i18n/label (cond (= step :recovery-success)
                              :t/recovery-success-text
                              :else (keyword (str "intro-wizard-text"
                                                  (when use-faceid? "-alt")
                                                  (step-kw-to-num step)))))]
           :else nil)]))

(defn enter-phrase [{:keys [processing?
                            passphrase-word-count
                            next-button-disabled?
                            passphrase-error] :as wizard-state}]
  [react/keyboard-avoiding-view {:flex             1
                                 :justify-content  :space-between
                                 :background-color colors/white}
   [react/view {:flex 1
                :justify-content :flex-start
                :align-items    :center}
    [text-input/text-input-with-label
     {:on-change-text    #(re-frame/dispatch [:multiaccounts.recover/enter-phrase-input-changed (security/mask-data %)])
      :auto-focus        true
      :on-submit-editing #(re-frame/dispatch [:multiaccounts.recover/enter-phrase-input-submitted])
      :error             (when passphrase-error (i18n/label passphrase-error))
      :placeholder       nil
      ;:height            120
      :bottom-value      40
      :multiline         true
      :auto-correct      false
      :keyboard-type     "visible-password"
      :parent-container  {:flex 1
                          :align-self :stretch
                          :justify-content :center
                          :align-items :center}
      :container         {:background-color :white
                          :flex 1
                          :justify-content :center
                          :align-items :center}
      :style             {:background-color :white
                          :text-align       :center
                          ;:height 75
                          :flex 1
                          :flex-wrap :wrap
                          :font-size        16
                          :font-weight      "700"}}]]
   [react/view {:align-items :center}
    (when passphrase-word-count
      [react/view {:flex-direction :row
                   :height         11
                   :margin-bottom 8
                   :align-items    :center}
       [react/text {:style {:font-size    14
                            :text-align   :center
                            :color        colors/gray}}
        (str (i18n/label  :t/word-count) ":")]
       [react/text {:style {:font-size          14
                            :padding-horizontal 4
                            :font-weight       "500"
                            :text-align         :center
                            :color              colors/black}}
        (i18n/label-pluralize passphrase-word-count :t/words-n)]
       (when-not next-button-disabled?
         [react/view {:style {:background-color colors/green-transparent-10
                              :border-radius 12
                              :width 24
                              :justify-content :center
                              :align-items :center
                              :height 24}}
          [vector-icons/tiny-icon :tiny-icons/tiny-check {:color colors/green}]])])
    [react/text {:style {:color      colors/gray
                         :font-size  14
                         :margin-bottom 8
                         :text-align :center}}
     (i18n/label :t/multiaccounts-recover-enter-phrase-text)]]
   (when processing?
     [react/view {:flex 1 :align-items :center}
      [react/activity-indicator {:size      :large
                                 :animating true}]
      [react/text {:style {:color      colors/gray
                           :margin-top 8}}
       (i18n/label :t/processing)]])])

(defn recovery-success [wizard-state]
  (let [pubkey (get-in wizard-state [:derived constants/path-whisper-keyword :publicKey])]
    [react/view {:flex           1
                 :justify-content  :space-between
                 :background-color colors/white}
     [react/view {:flex            1
                  :justify-content :space-between
                  :align-items     :center}
      [react/view {:flex-direction  :column
                   :flex            1
                   :justify-content :center
                   :align-items     :center}
       [react/view {:margin-horizontal 16
                    :flex-direction    :column}
        [react/view {:justify-content :center
                     :align-items     :center
                     :margin-bottom   11}
         [react/image {:source {:uri (identicon/identicon pubkey)}
                       :style  {:width         61
                                :height        61
                                :border-radius 30
                                :border-width  1
                                :border-color  colors/black-transparent}}]]
        [react/text {:style           {:text-align  :center
                                       :color       colors/black
                                       :font-weight "500"}
                     :number-of-lines 1
                     :ellipsize-mode  :middle}
         (gfy/generate-gfy pubkey)]
        [react/text {:style           {:text-align  :center
                                       :margin-top  4
                                       :color       colors/gray
                                       :font-family "monospace"}
                     :number-of-lines 1
                     :ellipsize-mode  :middle}
         (utils/get-shortened-address pubkey)]]]]]))

(defn intro-wizard [{:keys [step generating-keys?
                            biometric-auth back-action
                            view-width view-height] :as wizard-state}]
  (log/info "#intro-wizard" wizard-state)
  [react/keyboard-avoiding-view {:style {:flex 1}}
   [toolbar/toolbar
    {:style {:border-bottom-width 0
             :margin-top 16}}
    (when-not (#{:enable-fingerprint :enable-notifications} step)
      (toolbar/nav-button
       (actions/back #(re-frame/dispatch [back-action]))))
    nil]
   [react/view {:style {:flex 1
                        :justify-content :space-between}}
    [top-bar wizard-state]
    (case step
      :generate-key [generate-key view-height]
      :choose-key [choose-key wizard-state view-height]
      :select-key-storage [select-key-storage wizard-state view-height]
      :create-code [create-code wizard-state view-width]
      :confirm-code [confirm-code wizard-state view-width]
      :enable-fingerprint [enable-fingerprint biometric-auth]
      :enable-notifications [enable-notifications]
      :recovery-success [recovery-success wizard-state]
      :enter-phrase [enter-phrase wizard-state]
      nil nil)
    [bottom-bar wizard-state]]])

(defview wizard []
  (letsubs [wizard-state [:intro-wizard]]
    [intro-wizard wizard-state]))
