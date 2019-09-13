(ns status-im.ui.screens.multiaccounts.recover.views
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [re-frame.core :as re-frame]
            [status-im.ui.components.text-input.view :as text-input]
            [status-im.ui.components.react :as react]
            [status-im.ui.components.toolbar.view :as toolbar]
            [status-im.multiaccounts.recover.core :as multiaccounts.recover]
            [status-im.hardwallet.core :as hardwallet]
            [status-im.i18n :as i18n]
            [status-im.ui.components.styles :as components.styles]
            [status-im.utils.config :as config]
            [status-im.ui.components.common.common :as components.common]
            [status-im.utils.security :as security]
            [status-im.ui.components.colors :as colors]
            [status-im.utils.gfycat.core :as gfy]
            [status-im.utils.identicon :as identicon]
            [status-im.ui.components.icons.vector-icons :as vector-icons]
            [status-im.ui.screens.intro.views :as intro.views]
            [status-im.utils.utils :as utils]
            [status-im.constants :as constants]
            [status-im.ui.components.list-item.views :as list-item]
            [status-im.utils.platform :as platform]))

(defn bottom-sheet-view []
  [react/view {:flex 1 :flex-direction :row}
   [react/view {:flex 1}
    [list-item/list-item
     {:theme               :action
      :title               :t/enter-seed-phrase
      :accessibility-label :enter-seed-phrase-button
      :icon                :main-icons/text
      :on-press            #(re-frame/dispatch [::multiaccounts.recover/enter-phrase-pressed])}]
    (when (and config/hardwallet-enabled?
               platform/android?)
      [list-item/list-item
       {:theme               :action
        :title               :t/recover-with-keycard
        :disabled?           (not config/hardwallet-enabled?)
        :accessibility-label :recover-with-keycard-button
        :icon                :main-icons/keycard-logo
        :on-press            #(re-frame/dispatch [::hardwallet/recover-with-keycard-pressed])}])]])

(def bottom-sheet
  {:content        bottom-sheet-view
   :content-height (if platform/android? 130 65)})

(defview enter-phrase []
  (letsubs [{:keys [processing?
                    passphrase-error
                    words-count
                    next-button-disabled?]} [:get-recover-multiaccount]]
    [react/keyboard-avoiding-view {:flex             1
                                   :justify-content  :space-between
                                   :background-color colors/white}
     [toolbar/toolbar
      {:style {:border-bottom-width 0
               :margin-top 16}}
      [toolbar/nav-text
       {:handler #(re-frame/dispatch [::multiaccounts.recover/cancel-pressed])
        :style   {:padding-left 21}}
       (i18n/label :t/cancel)]
      [react/text {:style {:color colors/gray}}
       (i18n/label :t/step-i-of-n {:step   "1"
                                   :number "2"})]]
     [react/view {:flex 1
                  :justify-content :flex-start
                  :align-items    :center}
      [react/view {:margin-top 16}
       [react/text {:style {:typography :header
                            :text-align :center}}
        (i18n/label :t/multiaccounts-recover-enter-phrase-title)]]
      [text-input/text-input-with-label
       {:on-change-text    #(re-frame/dispatch [::multiaccounts.recover/enter-phrase-input-changed (security/mask-data %)])
        :auto-focus        true
        :on-submit-editing #(re-frame/dispatch [::multiaccounts.recover/enter-phrase-input-submitted])
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
      (when words-count
        [react/view {:flex-direction :row
                     :height         11
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
          (i18n/label-pluralize words-count :t/words-n)]
         (when-not next-button-disabled?
           [react/view {:style {:background-color colors/green-transparent-10
                                :border-radius 12
                                :width 24
                                :justify-content :center
                                :align-items :center
                                :height 24}}
            [vector-icons/tiny-icon :tiny-icons/tiny-check {:color colors/green}]])])
      (when next-button-disabled?
        [react/text {:style {:color      colors/gray
                             :font-size  14
                             :margin-top 8
                             :text-align :center}}
         (i18n/label :t/multiaccounts-recover-enter-phrase-text)])]
     (if processing?
       [react/view {:flex 1 :align-items :center}
        [react/activity-indicator {:size      :large
                                   :animating true}]
        [react/text {:style {:color      colors/gray
                             :margin-top 8}}
         (i18n/label :t/processing)]]
       [react/view {:flex-direction  :row
                    :align-self :stretch
                    :border-top-width 1
                    :padding-top 16
                    :margin-top 8
                    :margin-bottom 20
                    :justify-content  :flex-end
                    :border-top-color colors/gray-lighter
                    :align-items     :center}
        [react/view {:margin-right 10}
         [components.common/bottom-button
          {:on-press  #(re-frame/dispatch [::multiaccounts.recover/enter-phrase-next-pressed])
           :label     (i18n/label :t/next)
           :disabled? next-button-disabled?
           :forward?  true}]]])]))

(defview success []
  (letsubs [multiaccount [:get-recover-multiaccount]]
    (let [pubkey (get-in multiaccount [:derived constants/path-whisper-keyword :publicKey])]
      [react/view {:flex             1
                   :justify-content  :space-between
                   :background-color colors/white}
       [toolbar/toolbar
        {:transparent? true
         :style        {:margin-top 32}}
        nil
        nil]
       [react/view {:flex            1
                    :flex-direction  :column
                    :justify-content :space-between
                    :align-items     :center}
        [react/view {:flex-direction :column
                     :align-items    :center}
         [react/view {:margin-top 16}
          [react/text {:style {:typography :header
                               :text-align :center}}
           (i18n/label :t/keycard-recovery-success-header)]]
         [react/view {:margin-top  16
                      :width       "85%"
                      :align-items :center}
          [react/text {:style {:color      colors/gray
                               :text-align :center}}
           (i18n/label :t/recovery-success-text)]]]
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
           (utils/get-shortened-address pubkey)]]]
        [react/view {:margin-bottom 50}
         [react/touchable-highlight
          {:on-press #(re-frame/dispatch [:multiaccounts.recover/re-encrypt-pressed])}
          [react/view {:background-color colors/blue-light
                       :align-items      :center
                       :justify-content  :center
                       :flex-direction   :row
                       :width            193
                       :height           44
                       :border-radius    10}
           [react/text {:style {:color colors/blue}}
            (i18n/label :t/re-encrypt-key)]]]]]])))

(defn select-storage []
  [intro.views/wizard])

(defn enter-password []
  [intro.views/wizard])

(defn confirm-password []
  [intro.views/wizard])
