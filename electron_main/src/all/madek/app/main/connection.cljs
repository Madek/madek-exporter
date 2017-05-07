(ns madek.app.main.connection
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    )
  (:require
    [madek.app.main.state :as state]

    [cljs-http.client :as http]
    [cljs.nodejs :as nodejs]
    [cljs.core.async :refer [<!]]
    [clojure.walk]
    ))

; FCK http://stackoverflow.com/questions/32604460/xmlhttprequest-module-not-defined-found

(def Electron (nodejs/require "electron"))

(.on (.-ipcMain Electron) "madek:connect"
     (fn [event data]
       (.log js/console "CONNECT" data)
       (swap! state/db assoc-in [:connection :status] :connecting)
       (let [data (-> data js->clj clojure.walk/keywordize-keys )
             req {:method :get
                  :headers {"accept" "application/json"}
                  :url (str (:url data) "/api/")}]
         (.log js/console (clj->js req))
         (go (let [resp (<! (http/request req))]
               (.log js/console (clj->js resp)))))))
