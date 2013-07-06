(ns tinsel.zip
  "Zipper support for tinsel."
  (:require [clojure.zip :as zip]
            [tinsel.utils :as utils]))

;;
;; A zipper for hiccup forms.
;;
;; Just to make things easier, we go ahead and do the work here to make
;; hiccup zippers work on both normalized and unnormalized hiccup forms.


(defn- children
  "Takes a hiccup node (normalized or not) and returns its children nodes."
  [node]
  (if (map? (second node))   ;; There is an attr map in second slot.
    (seq (subvec node 2))    ;; So skip tag and attr vec.
    (seq (subvec node 1))))  ;; Otherwise, just skip tag.

;; Note, it's not made clear at all in the docs for clojure.zip, but as far as
;; I can tell, you are given a node potentially with existing children and
;; the sequence of children that should totally replace the existing children.
(defn- make
  "Takes a hiccup node (normalized or not) and a sequence of children nodes,
   and returns a new node that has the the children argument as its children."
  [node children]
  (if (map? (second node))                ;; Again, check for normalized vec.
    (into (subvec node 0 2) children)     ;; Attach children after tag&attrs.
    (apply vector (first node) children)));; Otherwise, attach after tag.

(defn hiccup-zip
  "Returns a zipper for Hiccup forms, given a root form."
  [root]
  (zip/zipper vector?
              children
              make
              root))

(defn do-tree
  "Applies the first argument to the nodes of the tree, moving to the next
   node by moving to the result of the next-fn argument applied to the current
   node until the end is reached. By default, the next-fn argument is
   zip/next. Not lazy, so do-fn can have side-effects."
  ([do-fn start-loc]
     (do-tree do-fn start-loc zip/next))
  ([do-fn start-loc next-fn]
     (loop [loc start-loc]
       (if (zip/end? loc)
         (do-fn (zip/root loc))
         (recur (next-fn (do (do-fn (zip/node loc))
                             loc)))))))

(defn print-tree
  "Prints the nodes of the tree by repeatedly applying the argument
   function to the location until the end is reached. By default, the
   next-fn argument is zip/next."
  ([start-loc]
     (do-tree println start-loc))
  ([start-loc next-fn]
     (do-tree println start-loc next-fn)))

;;
;; Postorder traversal
;;
;; Clojure.zip only provides support for preorder tree walking with next/prev.
;; For the semantics we want, which is that leaves get transformed first, we
;; need to walk the tree in postorder. We add the functions we need to support
;; that here. Note that unlike with clojure.zip's default walk order, the root
;; is not the first node in a postorder walk, so start things off at the result
;; of postorder-first instead of the initial loc.

(defn leftmost-descendant
  "Given a zipper loc, returns its leftmost descendent (ie, down repeatedly)."
  [loc]
  (if (and (zip/branch? loc) (zip/down loc))
    (recur (zip/down loc))
    loc))

(defn postorder-first
  "Given a root node, returns the first node of a postorder tree walk. See
   comment on postorder-next."
  [loc]
  (leftmost-descendant loc))

(defn postorder-next
  "Moves to the next loc in the hierarchy in postorder traversal. Behaves like
   clojure.zip/next otherwise. Note that unlike with a pre-order walk, the root
   is NOT the first element in the walk order, so be sure to take that into
   account in your algorithm if it matters (ie, call postorder-first first
   thing before processing a node)."
  [loc]
  (if (= :end (loc 1)) ;; If it's the end, return the end.
    loc
    (if (nil? (zip/up loc))
      ;; Node has no parent, this is the root/final node, return the end.
      [(zip/node loc) :end]
      ;; Node is internal, so we got to it by having traversed its children.
      ;; Instead, we want to try to move to the leftmost descendant of our
      ;; right sibling, if possible.
      (or (and (zip/right loc) (leftmost-descendant (zip/right loc)))
          ;; There was no right sibling, we must move back up the tree.
          (zip/up loc)))))

(defn root-loc
  "Moves to the root zipper location."
  [loc]
  (if (= :end (loc 1))
    loc
    (loop [p (zip/up loc)]
      (if p
        (recur p)
        loc))))

(defn- postorder-seq-from-loc
  "Returns a postwalk order seq of nodes, from the current zipper location."
  [loc]
  (lazy-seq
   (when-not (zip/end? loc)
     (cons loc (postorder-seq-from-loc (postorder-next loc))))))

(defn postorder-seq
  "Returns a seq of all the zipper nodes, in postwalk order."
  [loc]
  (-> loc root-loc postorder-first postorder-seq-from-loc))
