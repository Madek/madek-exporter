(ns madek.app.main.menu
  (:require
    [cljs.nodejs :as nodejs]
    [madek.app.main.windows]
    )
  )

(def Electron (nodejs/require "electron"))

(def Menu (.-Menu Electron))

(def new-window-subitem
  {:label "New Window"
   :click #(madek.app.main.windows/open-new)
   :accelerator "CommandOrControl+N"
   })

(def toggle-deftools-subitem
  {:label "Toggle Developer Tools"
   :click (fn [_ win _]
            (when win
              (-> win .-webContents .toggleDevTools)))
   :accelerator (case (.-platform nodejs/process)
                  "darwin" "Alt+Command+I"
                  "Ctrl+Shift+I")})

(def reload-page-subitem
  {:label "Reload"
   :click (fn [_ win _]
            (when win
              (-> win .-webContents .reload)))
   :accelerator "CommandOrControl+R" })

(def menu-template
  (->> [(when (= (.-platform nodejs/process) "darwin")
          {:label "Madek"
           :submenu [{:role "quit"}
                     ]})
        {:label "File"
         :submenu [{:role "copy"}
                   {:role "paste"}
                   {:type "separator"}
                   new-window-subitem]}
        {:label "Window"
         :submenu [{:role "close"}
                   {:type "separator"}
                   reload-page-subitem
                   toggle-deftools-subitem
                   {:type "separator"}
                   new-window-subitem
                   ]}]
       (filter identity)))

(.log js/console "menu" (clj->js menu-template))

(def menu (.buildFromTemplate Menu (clj->js menu-template)))

(defn initialize []
  (.setApplicationMenu Menu menu)
  )

;

