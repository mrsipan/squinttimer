(ns app.core)

;; ── State ──────────────────────────────────────────────────────────
(defonce state (atom {:total-seconds (* 30 60)
                      :remaining-seconds (* 30 60)
                      :running? false
                      :setting? false
                      :dragging? false}))

(defonce timer-id (atom nil))

;; ── Helpers ────────────────────────────────────────────────────────
(def max-seconds 1800)

(defn format-time [secs]
  (let [m (int (/ secs 60))
        s (mod secs 60)]
    (str (if (< m 10) "0" "") m ":" (if (< s 10) "0" "") s)))

(defn percentage []
  (let [{:keys [total-seconds remaining-seconds]} @state]
    (if (zero? total-seconds)
      0
      (* (/ remaining-seconds total-seconds) 100))))

;; ── Handle position math ───────────────────────────────────────────
(def center 150)
(def radius 150)

(defn minutes->handle-xy [minutes]
  "Convert minutes (0-30) to x,y for handle center.
   angle from 12 o'clock clockwise: (30 - minutes) / 30 * 2π
   minutes=30 -> angle=0 (top), minutes=15 -> angle=π (bottom), minutes=0 -> angle=2π (top)"
  (let [angle (* (/ (- 30 minutes) 30) 2 js/Math.PI)
        hx (+ center (* radius (js/Math.sin angle)))
        hy (- center (* radius (js/Math.cos angle)))]
    {:x hx :y hy}))

(defn mouse->minutes [client-x client-y]
  "Convert mouse position relative to disc center to minutes (0-30).
   minutes = 30 - (atan2(dy,dx) + π/2) * 30/π, normalized to 0-30."
  (let [disc (js/document.getElementById "timer-disc")
        rect (.getBoundingClientRect disc)
        cx (+ (.-left rect) (/ (.-width rect) 2))
        cy (+ (.-top rect) (/ (.-height rect) 2))
        dx (- client-x cx)
        dy (- client-y cy)
        raw (- 30 (* (+ (js/Math.atan2 dy dx) (/ js/Math.PI 2)) (/ 30 js/Math.PI)))
        normalized (mod raw 30)]
    (if (neg? normalized) (+ normalized 30) normalized)))

;; ── DOM references (created once) ──────────────────────────────────
(defonce dom-refs (atom nil))

(defn create-dom! []
  (let [app (js/document.getElementById "app")
        disc (js/document.createElement "div")
        time-div (js/document.createElement "div")
        handle (js/document.createElement "div")
        btn-bar (js/document.createElement "div")
        btn-start (js/document.createElement "button")
        btn-reset (js/document.createElement "button")]
    ;; disc
    (set! (.-id disc) "timer-disc")
    (set! (.-innerHTML disc) "")
    (set! (.-style disc) "width:300px;height:300px;border-radius:50%;border:4px solid #ccc;position:relative;cursor:pointer;")
    ;; time display centered
    (set! (.-id time-div) "time-display")
    (set! (.-style time-div) "position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);font-size:3rem;font-weight:bold;color:#333;pointer-events:none;")
    (.appendChild disc time-div)
    ;; handle
    (set! (.-id handle) "timer-handle")
    (set! (.-style handle) "position:absolute;width:24px;height:24px;border-radius:50%;background:white;border:3px solid red;cursor:grab;z-index:10;transform:translate(-50%,-50%);pointer-events:auto;")
    (.appendChild disc handle)
    ;; buttons
    (set! (.-id btn-start) "btn-start")
    (set! (.-id btn-reset) "btn-reset")
    (set! (.-style btn-bar) "margin-top:1.5rem;display:flex;gap:1rem;")
    (.appendChild btn-bar btn-start)
    (.appendChild btn-bar btn-reset)
    ;; assemble
    (set! (.-style app) "display:flex;flex-direction:column;align-items:center;font-family:sans-serif;padding:2rem;")
    (set! (.-innerHTML app) "")
    (.appendChild app disc)
    (.appendChild app btn-bar)
    ;; store refs
    (reset! dom-refs {:disc disc :time-div time-div :handle handle :btn-start btn-start :btn-reset btn-reset})))

;; ── Update UI (no innerHTML replacement) ───────────────────────────
(defn update-ui []
  (let [{:keys [total-seconds remaining-seconds running? setting?]} @state
        time-str (format-time remaining-seconds)
        {:keys [disc time-div handle btn-start btn-reset]} @dom-refs
        xy (minutes->handle-xy (/ remaining-seconds 60))
        pct (if setting?
              ;; During drag: show percentage relative to max (30 min = 1800 sec)
              ;; because total-seconds == remaining-seconds during drag
              (let [v (* (/ total-seconds max-seconds) 100)]
                (if (neg? v) 0 (if (> v 100) 100 v)))
              ;; Running or paused: normal countdown percentage
              (percentage))]
    ;; disc background
    (set! (.-background (.-style disc)) (str "conic-gradient(red 0% " pct "%, transparent " pct "% 100%)"))
    ;; time text
    (set! (.-textContent time-div) time-str)
    ;; handle position
    (set! (.-left (.-style handle)) (str (:x xy) "px"))
    (set! (.-top (.-style handle)) (str (:y xy) "px"))
    ;; button text
    (set! (.-textContent btn-start) (if running? "PAUSE" "START"))
    (set! (.-textContent btn-reset) "RESET")))

;; ── Countdown ──────────────────────────────────────────────────────
(defn start-countdown []
  (when (nil? @timer-id)
    (reset! timer-id (js/setInterval
      (fn []
        (let [s @state]
          (when (and (:running? s) (> (:remaining-seconds s) 0))
            (swap! state update :remaining-seconds dec)
            (update-ui))
          (when (and (:running? s) (zero? (:remaining-seconds s)))
            (swap! state assoc :running? false)
            (update-ui))))
      1000))))

(defn stop-countdown []
  (when @timer-id
    (js/clearInterval @timer-id)
    (reset! timer-id nil)))

;; ── Event handlers ─────────────────────────────────────────────────
(defn handle-start []
  (let [s @state]
    (if (:running? s)
      (do (swap! state assoc :running? false) (stop-countdown) (update-ui))
      (do (swap! state assoc :running? true) (start-countdown) (update-ui)))))

(defn handle-reset []
  (let [s @state]
    (stop-countdown)
    (swap! state assoc :running? false :remaining-seconds (:total-seconds s))
    (update-ui)))

;; ── Handle drag ────────────────────────────────────────────────────
(defn handle-mousedown [e]
  (let [target (.-target e)
        handle (js/document.getElementById "timer-handle")]
    (when (or (= target handle) (.contains handle target))
      (.preventDefault e)
      (swap! state assoc :dragging? true :setting? true)
      (let [mins (mouse->minutes (.-clientX e) (.-clientY e))
            total-secs (int (* mins 60))]
        (swap! state assoc :total-seconds total-secs :remaining-seconds total-secs)
        (update-ui)))))

(defn handle-mousemove [e]
  (when (:dragging? @state)
    (.preventDefault e)
    (let [mins (mouse->minutes (.-clientX e) (.-clientY e))
          total-secs (int (* mins 60))]
      (swap! state assoc :total-seconds total-secs :remaining-seconds total-secs)
      (update-ui))))

(defn handle-mouseup [e]
  (when (:dragging? @state)
    (swap! state assoc :dragging? false :setting? false)))

;; ── Init ───────────────────────────────────────────────────────────
(defn init []
  (create-dom!)
  (update-ui)
  (let [handle (js/document.getElementById "timer-handle")]
    (.addEventListener handle "mousedown" handle-mousedown))
  (.addEventListener js/document "mousemove" handle-mousemove)
  (.addEventListener js/document "mouseup" handle-mouseup)
  (.addEventListener (js/document.getElementById "btn-start") "click" handle-start)
  (.addEventListener (js/document.getElementById "btn-reset") "click" handle-reset))

(init)
