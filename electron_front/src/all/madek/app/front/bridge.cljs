(ns madek.app.front.bridge
  "Renderer access to Electron via contextBridge preload (`window.madekBridge`).
  Named madekBridge (not madek) so it does not collide with the CLJS ns root.")

(defn api []
  (.-madekBridge js/window))

(defn- with-api [f]
  (if-let [a (api)]
    (f a)
    (js/console.error "window.madekBridge missing; is preload.js loaded?")))

(defn choose-directory [default-path]
  (with-api #(.chooseDirectory % (or default-path ""))))

(defn open-external [url]
  (with-api #(.openExternal % url)))

(defn open-path [dir]
  (with-api #(.openPath % dir)))

(defn on-jvm-sync-init [handler]
  (with-api #(.onJvmSyncInit % handler)))

(defn on-navigate [handler]
  (with-api #(.onNavigate % handler)))

(defn on-db-full [handler]
  (with-api #(.onDbFull % handler)))

(defn on-db-patch [handler]
  (with-api #(.onDbPatch % handler)))

(defn versions []
  (or (with-api #(js->clj (.getVersions %) :keywordize-keys true))
      {}))

(defn platform []
  (or (with-api #(.platform %))
      (some-> (aget js/window "navigator") .-platform)
      ""))
