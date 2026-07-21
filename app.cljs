(ns app.core)

;; ── State ──────────────────────────────────────────────────────────
(defonce state (atom {:total-seconds (* 30 60)
                      :remaining-seconds (* 30 60)
                      :running? false
                      :setting? false}))

(defonce timer-id (atom nil))

;; ── Helpers ────────────────────────────────────────────────────────
(defn angle->minutes [angle-deg]
  (let [normalized (mod (+ angle-deg 90) 360)]
    (/ normalized 6)))

(defn format-time [secs]
  (let [m (int (/ secs 60))
        s (mod secs 60)]
    (str (if (< m 10) "0" "") m ":" (if (< s 10) "0" "") s)))

(defn percentage []
  (let [{:keys [total-seconds remaining-seconds]} @state]
    (if (zero? total-seconds)
      0
      (* (/ remaining-seconds total-seconds) 100))))

;; ── Rendering ──────────────────────────────────────────────────────
(defn render []
  (let [{:keys [total-seconds remaining-seconds running?]} @state
        pct (percentage)
        time-str (format-time remaining-seconds)
        disc-style (str "background: conic-gradient(red 0% " pct "%, transparent " pct "% 100%);")]
    (set! (.-innerHTML (js/document.getElementById "app"))
      (str "<div style='display:flex;flex-direction:column;align-items:center;font-family:sans-serif;padding:2rem;'>"
           "<div style='width:300px;height:300px;border-radius:50%;" disc-style
           "border:4px solid #ccc;position:relative;cursor:pointer;' "
           "id='timer-disc'>"
           "<div style='position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);"
           "font-size:3rem;font-weight:bold;color:#333;'>" time-str "</div>"
           "</div>"
           "<div style='margin-top:1.5rem;display:flex;gap:1rem;'>"
           "<button id='btn-start'>" (if running? "PAUSE" "START") "</button>"
           "<button id='btn-reset'>RESET</button>"
           "</div>"
           "</div>"))))

;; ── Countdown ──────────────────────────────────────────────────────
(defn start-countdown []
  (when (nil? @timer-id)
    (reset! timer-id (js/setInterval
      (fn []
        (let [s @state]
          (when (and (:running? s) (> (:remaining-seconds s) 0))
            (swap! state update :remaining-seconds dec)
            (render))
          (when (and (:running? s) (zero? (:remaining-seconds s)))
            (swap! state assoc :running? false)
            (render))))
      1000))))

(defn stop-countdown []
  (when @timer-id
    (js/clearInterval @timer-id)
    (reset! timer-id nil)))

(defn handle-start []
  (let [s @state]
    (if (:running? s)
      (do (swap! state assoc :running? false) (stop-countdown) (render))
      (do (swap! state assoc :running? true) (start-countdown) (render)))))

(defn handle-reset []
  (let [s @state]
    (stop-countdown)
    (swap! state assoc :running? false :remaining-seconds (:total-seconds s))
    (render)))

;; ── Drag to set ────────────────────────────────────────────────────
(defn get-angle [client-x client-y]
  (let [disc (js/document.getElementById "timer-disc")
        rect (.getBoundingClientRect disc)
        cx (+ (.-left rect) (/ (.-width rect) 2))
        cy (+ (.-top rect) (/ (.-height rect) 2))
        dx (- client-x cx)
        dy (- client-y cy)]
    (* (/ (js/Math.atan2 dy dx) js/Math.PI) 180)))

(defn handle-mousedown [e]
  (swap! state assoc :setting? true)
  (let [angle (get-angle (.-clientX e) (.-clientY e))
        mins (angle->minutes angle)
        total-secs (int (* mins 60))]
    (swap! state assoc :total-seconds total-secs :remaining-seconds total-secs)
    (render)))

(defn handle-mousemove [e]
  (when (:setting? @state)
    (let [angle (get-angle (.-clientX e) (.-clientY e))
          mins (angle->minutes angle)
          total-secs (int (* mins 60))]
      (swap! state assoc :total-seconds total-secs :remaining-seconds total-secs)
      (render))))

(defn handle-mouseup [e]
  (swap! state assoc :setting? false))

(defn handle-mouseleave [e]
  (swap! state assoc :setting? false))

;; ── Wire events ────────────────────────────────────────────────────
(defn init []
  (render)
  (let [disc (js/document.getElementById "timer-disc")]
    (.addEventListener disc "mousedown" handle-mousedown))
  (.addEventListener js/document "mousemove" handle-mousemove)
  (.addEventListener js/document "mouseup" handle-mouseup)
  (.addEventListener (js/document.getElementById "btn-start") "click" handle-start)
  (.addEventListener (js/document.getElementById "btn-reset") "click" handle-reset))

(init)
