
(ns backend.server
  (:require ["body-parser" :as body-parser]
            ["compression" :as compression]
            ["express" :as express] 
            ["fs" :as fs]
            ["form-data" :as FormData]
            ["node:process" :as process]
            ["path" :as path]
            ["puppeteer" :as puppeteer]
            [cljs.core.async :refer [<! >! chan go go-loop]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [config   :refer [port]]))

(.on process
     "uncaughtException" (fn [err origin]
                           (.log js/console "Uncaught exception: " err origin)))



(defn wait [ms]
  (js/Promise. (fn [resolve]
                 (js/setTimeout resolve ms))))



(defn run-puppeteer [{:keys [url class res]}]
  (let [data (atom {})]
    (-> (puppeteer/launch)
        (.then (fn [browser]
                 (-> (.newPage browser)
                     (.then (fn [page]
                              (let [app (.goto page url)
                                    class (str "." class)
                                    finished-channel (chan)] 
                                (-> app
                                    (.then #(wait 1000)) 
                                    (.then #(.$$ page class)) ; Select the div
                                    (.then (fn [elements] 
                                             (let [element-count (count elements)] 
                                               (go-loop [index 0] 
                                                 (if (< index element-count)
                                                   (let [file-name (str index ".png")
                                                         file-path (str "screenshots/" file-name)
                                                         make-screenshot (<p! (.screenshot
                                                                               (get elements index)
                                                                               #js{:path file-path}))
                                                         base64-image (.toString (fs/readFileSync file-path)
                                                                                 "base64")]
                                                     
                                                     (reset! data (assoc @data file-name base64-image)) 
                                                     (recur (inc index)))
                                                   (>! finished-channel 1))))))
                                    (go (<! finished-channel)                          
                                        (.close browser) 
                                        (.send res (str @data))
                                        ))))))))
        (.catch #(js/console.error %)))))




(defonce server (atom nil))



(defn start-server []
  (let [app   (express)
        new-port  (or (.-PORT (.-env process)) port)]
    (.use app (compression))
    (.use app (express/static "../frontend/public")) 
    (.use app (.urlencoded body-parser #js {:limit "100mb"
                                            :extended    true})) 
    (.use app (.json body-parser #js {:limit "100mb"})) 
    (.get app  "/"                     
          (fn [req res] 
            (run-puppeteer 
             {:res res
              :url "https://alpha.wizard.xyz/scroll/d704f634-5b3f-4099-aa22-dc7c61bee23e?version=1720152785349"
              :class "section"})
            (.send res "Puppeteer API"))) 
    (.post app  "/url"
          (fn [req res]
            (let [url (-> req .-body .-url)
                  class (-> req .-body .-class)] 
              (.log js/console "Taking screenshots of: " url 
                    " "  (js/Date.now))
              (try (run-puppeteer 
                    {:res res 
                     :url url
                     :class class})
                   (catch js/Error e (.send res "Error")))))) 
    
    (.listen app new-port "::" "0.0.0.0" 
             (fn [] (println "Port: " new-port)))))

  
(defn stop! []
    (when @server (.close @server)))


(defn start! []
  (println "Code updated.")
  (reset! server (start-server)))



