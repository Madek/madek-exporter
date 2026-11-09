(ns madek.app.front.bridge
  "Renderer access to Electron via contextBridge preload (`window.madek`).")

(defn api []
  (.-madek js/window))

(defn choose-directory [default-path]
  (.chooseDirectory (api) (or default-path "")))

(defn open-external [url]
  (.openExternal (api) url))

(defn open-path [dir]
  (.openPath (api) dir))

(defn on-jvm-sync-init [handler]
  (.onJvmSyncInit (api) handler))

(defn on-navigate [handler]
  (.onNavigate (api) handler))

(defn on-db-full [handler]
  (.onDbFull (api) handler))

(defn on-db-patch [handler]
  (.onDbPatch (api) handler))

(defn versions []
  (js->clj (.getVersions (api)) :keywordize-keys true))
