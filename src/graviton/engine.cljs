(ns graviton.engine
  (:require
    [clojure.walk :refer [prewalk]]
    [reagent.core :as r]))

(defn load-texture [resource-name]
  (.fromImage (.-Texture js/PIXI) (str "assets/" resource-name)))

(defn sprite [resource-name]
  (let [sprite (js/PIXI.Sprite. (load-texture resource-name))]
    (.set (.-anchor sprite) 0.5 0.5)
    sprite))

(defn set-sprite-position [{:keys [sprite x y velocity width height]}]
  (set! (.-x (.-position sprite)) x)
  (set! (.-y (.-position sprite)) y)
  (set! (.-rotation sprite) (js/Math.atan2 (:y velocity) (:x velocity)))
  (when width (set! (.-width sprite) width))
  (when height (set! (.-height sprite) height)))

(defn add-to-stage [stage actor]
  (set-sprite-position actor)
  (.addChild stage (:sprite actor)))

(defn remove-from-stage [stage actor]
  (.removeChild stage (:sprite actor)))

(defn init-stage []
  (js/PIXI.Container.))

(defn canvas-coords [canvas]
  [(int (.-width canvas))
   (int (.-height canvas))])

(defn init-renderer [canvas width height]
  (js/PIXI.autoDetectRenderer width height (clj->js {:view canvas})))

(defn render [renderer stage]
  (.render renderer stage))

(defn gravitational-acceleration-at-point [px py actors]
  ;; (println (map :id actors))
  (apply merge-with +
         (map (fn [{:keys [x y mass]}] (let [dx (- px x)
                                             dy (- py y)
                                             r (/ mass (+ (* dx dx) (* dy dy) 0.0000001))
                                             theta (js/Math.atan (/ dy dx))]
                                         {:x (* (if (> px x) -1 1) r (js/Math.cos theta))
                                          :y (*  (if (>= px x) -1 1) r (js/Math.sin theta))})) actors)))

(defn sigmoid [v]
  (/ v (+ 1 (js/Math.abs v))))

(defn draw-gravity-vector [graphics x y state]
  (let [{ax :x ay :y :as acceleration} (gravitational-acceleration-at-point x y (:actors state))
        ax (* 50 ax)
        ay (* 50 ay)
        magnitude (+ (* ax ax) (* ay ay))
        redness 1
        greenness 70
        color (+ (* (js/Math.ceil (* 0xff (sigmoid (* magnitude redness)))) 0x10000) (- 0xff00 (* (js/Math.ceil (* 0xff (sigmoid (/ magnitude greenness)))) 0x100)))]
    (.moveTo graphics x y)
    (set! (.-lineColor graphics) color)
    (set! (.-lineWidth graphics) 2)
    (.lineTo graphics
             (+ x (* 3 (sigmoid ax)))
             (+ y (* 3 (sigmoid ay))))))

(defn draw-vector-field [state]
  (.clear (:vector-field state))
  (doall (for [x (map #(* 10 %) (range (js/Math.ceil (/ (:width state) 10))))
               y (map #(* 10 %) (range (js/Math.ceil (/ (:height state) 10))))]
           (draw-gravity-vector (:vector-field state) x y state))))

(defn render-loop [state-atom]
  ((fn frame []
     (let [{:keys [renderer stage actors vector-field] :as state} @state-atom]
       (draw-vector-field state)
       (render renderer stage)
       (js/requestAnimationFrame frame)))))

(defn add-actors-to-stage [state]
  (let [{:keys [stage actors]} @state]
    (prewalk
     (fn [node] (when (:sprite node) (add-to-stage stage node)) node)
     actors)))

(defn init-game-loop [state]
  (.add (:ticker @state)
        (fn [delta]
          (swap! state
                 #((:update %) (assoc % :delta delta))))))

(defn init-canvas [state]
  (fn [component]
    (let [canvas (r/dom-node component)
          width  (int (.-width canvas))
          height (int (.-height canvas))
          vector-field (js/PIXI.Graphics.)
          stage (init-stage)
          ticker (js/PIXI.ticker.Ticker.)]
      (swap! state assoc
             :vector-field vector-field
             :canvas canvas
             :width width
             :height height
             :stage stage
             :renderer (init-renderer canvas width height)
             :ticker ticker)
      (.addChild stage vector-field)
      (add-actors-to-stage state)
      (init-game-loop state)
      (.start ticker)
      (render-loop state))))
