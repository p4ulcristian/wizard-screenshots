
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
    (-> (puppeteer/launch #js {:executablePath "/usr/bin/chromium"
                               :args #js ["--no-sandbox" "--disable-setuid-sandbox"]})
        (.then (fn [^js browser]
                 (-> (.newPage browser)
                     (.then (fn [^js page]
                              (let [app (try (.goto page url) 
                                             (catch js/Error e (println "Error in url load")))
                                    dom-class (str "." class)
                                    finished-channel (chan)] 
                                (-> app
                                    (.then #(wait 2000)) 
                                    (.then #(.$$ page dom-class)) ; Select the div
                                    (.then (fn [^js elements] 
                                             (let [element-count (count elements)] 
                                               (go-loop [index 0] 
                                                 (if (< index element-count)
                                                   (let [file-name (str index ".png")
                                                         file-path (str "screenshots/" file-name)
                                                         make-screenshot (<p! 
                                                                          (let [^js element (get elements index)]
                                                                            (.screenshot element 
                                                                                         #js{:path file-path})))
                                                         ^js image    (fs/readFileSync file-path)
                                                         base64-image (.toString image "base64")]
                                                     
                                                     (reset! data (assoc @data file-name base64-image)) 
                                                     (recur (inc index)))
                                                   (>! finished-channel 1))))))
                                    (go (<! finished-channel)                          
                                        (.close browser) 
                                        (println "Sending response: ")
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
              :class "section"}))) 
    (.post app  "/url"
          (fn [^js req ^js res]
            (let [url (-> req .-body .-url)
                  class (-> req .-body .-class)] 
              (.log js/console "Taking screenshots of: " url 
                    " "  (js/Date.now) "with class: " class)
              (.log js/console "Body: " (.-body req))
              (try (run-puppeteer 
                    {:res res 
                     :url url
                     :class class})
                   (catch js/Error e (.log js/console "Error in puppeteer: " e)))))) 
    
    (.listen app new-port "::" "0.0.0.0" 
             (fn [] (println "Port: " new-port)))))

  
(defn stop! []
    (when @server (.close @server)))


(defn start! []
  (println "Code updated.")
  (reset! server (start-server)))



