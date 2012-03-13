(ns unlambda-clj.core)


(def cur-char (atom nil))

(defn- bounce
  "like trampoline, but evaluates to nil, and doesn't ever call the result of the function given to it."
  ([f] (trampoline #(do (f) nil)))
  ([f & args] (bounce #(apply f args))))

(defn evaluate
  ([program] (evaluate program identity))
  ([program result-callback]
     (letfn [(app [[func closure] arg cont]
 ;              (println (str "ARGS TO APP: " [[func closure] arg]))
               (case func
                 "." (do (.write *out* closure) (bounce cont arg))
                 "r" (do (.write *out* "\n") (bounce cont arg))
                 "i" (bounce cont arg)
                 "k" (bounce cont ["k1" arg])
                 "k1" (bounce cont closure)
                 "s" (bounce cont ["s1" arg])
                 "s1" (bounce cont ["s2" [closure, arg]])
                 "s2" (let [[f1 f2] closure] (ev ["`" [["`" [f1 arg]] ["`" [f2 arg]]]] cont))
                 "v" (bounce cont ["v", nil])
                 "d1" (ev ["`" [closure, arg]], #(bounce cont %))
                 "e" (result-callback arg)
                 "@" (do (reset! cur-char (.readChar *in*))
                         (ev ["`" [arg [(if @cur-char "i" "v") nil]]] cont))
                 "|" (ev ["`" [arg (if @cur-char ["." @cur-char] ["v" nil])]] cont)
                 "?" (ev ["`" [arg [(if (= @cur-char closure) "i" "v") nil]]] cont)
                 "c" (ev ["`" [arg ["c1" cont]]] cont)
                 "c1" (bounce closure arg)))
             (ev [[func closure] cont]
;               (println (str "ARGS TO EV: " [func closure]))
               (if-not (= func "`")
                 (bounce cont [func closure])
                 (let [[func arg] closure]
                   (recur func
                     (fn [op]
;                       (println (str "OP IS " op))
                       (if (= "d" (first op))
                         (bounce cont ["d1" arg])
                         (ev arg (fn [earg]
                                   (app op earg (fn [res] (bounce cont res)))))))))))]
       (ev program result-callback))))

(defn parse [program]
  (let [p (atom program)]
    (letfn [(snext [cs] (apply str (next cs)))
            (parse-out []
              (condp re-find @p
                #"^`" (do (swap! p snext) (let [a (parse-out), b (parse-out)] ["`" [a b]]))
                #"^[rksivdce@|]" (let [r [(str (first @p)) nil]] (swap! p snext) r)
                #"^[.?]." (let [result [(str (first @p)) (str (fnext @p))]]
                            (swap! p #(apply str (nnext %)))
                            result)
                #"^(\s+|\#.*)" :>> (fn [ws] (do (swap! p #(apply str (nthnext % (count (ws 1)))))
                                                (parse-out)))))]
      (parse-out))))

(defn interpret [code] (evaluate (parse code)))

 ;```s``sii`ki``s``s`ks ``s``s`ks``s`k`s`kr ``s`k`si``s`k`s`k
 ;`d````````````.H.e.l.l.o.,. .w.o.r.l.d.! k k `k``s``s`ksk`k.*
