(ns madek.menu
  (:require
    [cljs.nodejs :as nodejs]
    [madek.windows]
    )
  )

(def Electron (nodejs/require "electron"))

(def Menu (.-Menu Electron))

(def new-window-subitem
  {:label "New Window"
   :click #(madek.windows/open-new)
   :accelerator "CommandOrControl+N"
   })

(def menu-template
  (->> [(when (= (.-platform nodejs/process) "darwin")
          {:label (-> Electron .-app .getName )
           :submenu [{:role "quit"}
                     ]})
        {:label "File"
         :submenu [new-window-subitem]}
        {:label "Window"
         :submenu [{:role "close"}
                   {:type "separator"}
                   new-window-subitem
                   ]}]
       (filter identity)))

(.log js/console "menu" (clj->js menu-template))

(def menu (.buildFromTemplate Menu (clj->js menu-template)))

(defn initialize []
  (.setApplicationMenu Menu menu))

;

