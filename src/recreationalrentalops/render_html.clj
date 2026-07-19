(ns recreationalrentalops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`recreationalrentalops.operation` -> `recreationalrentalops.governor`
  -> `recreationalrentalops.store`) through a scenario built from this
  repo's own `recreationalrentalops.store/seed-db` demo data (units
  unit-1/unit-2/unit-3) and renders the result deterministically -- no
  invented numbers, no timestamps in the page content, byte-identical
  across reruns against the same seed (verified by diffing two
  consecutive runs before shipping).

  NOTE on this repo's own `recreationalrentalops.sim` demo driver
  (`clojure -M:dev:run`): unlike `cloud-itonami-isic-851`'s
  `schoolops.sim` (which referenced nonexistent resident-* ids from a
  copy-pasted eldercare actor and silently HARD-held on every call),
  this repo's own `sim.cljc` was checked BEFORE writing this file and
  found to drive the actual seeded unit-1/unit-2/unit-3 ids correctly
  (`clojure -M:dev:run` output confirms clean auto-commits, approved
  escalations and three distinct HARD holds, matching the actor's own
  documented behavior). This renderer keeps its own `run-demo!`
  scenario (a trimmed, representative subset of what `sim.cljc`
  exercises) so this build-time generator has no runtime dependency on
  the demo driver either way -- every field read by `render` below is
  real governor/store output, not a hand-typed copy.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [recreationalrentalops.store :as store]
            [recreationalrentalops.operation :as op]
            [recreationalrentalops.advisor :as advisor]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :fleet-manager :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach. HARD-hold attempts run FIRST (order matters
  only for which fact `last-fact-for` surfaces in the top summary table
  below -- the full history is always in the audit ledger regardless):
  unit-1's own advisor is swapped for one that claims a direct `:effect
  :commit` actuation, HARD-holding on `:effect-not-propose`; unit-2's
  advisor drifts into out-of-scope re-rent-without-inspection territory
  (`:out-of-scope? true`), HARD-holding on `:scope-excluded`; unit-3
  (registered but NOT `:verified?` in the seed data) HARD-holds a
  rental-record log on `:asset-unverified` -- none of these three ever
  reach a human. Then the clean paths: unit-1 clears a rental-record
  log and a fleet-operation (maintenance) schedule (both auto-commit
  clean at phase 3), then a fleet-restock coordination OVER
  `governor/high-cost-threshold` (ALWAYS escalates per
  `governor/high-cost-fleet-restock?` -- approved by a human fleet
  manager); unit-2 clears a fleet-restock coordination under the cost
  threshold (auto-commit clean), then an equipment-safety-concern flag
  (ALWAYS escalates per `governor/always-escalate-ops`, regardless of
  confidence -- approved). Every op keyword used here is a real member
  of `governor/allowed-ops`. Returns the resulting store -- every field
  read by `render` below is real governor/store output, not a
  hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        actor-direct (op/build db {:advisor (reify advisor/Advisor
                                               (-advise [_ _ req]
                                                 (assoc (advisor/infer nil req) :effect :commit)))})]
    (exec! actor-direct "u1-direct" {:op :schedule-fleet-operation :asset-id "unit-1"
                                      :patch {:item "annual safety recertification"}})

    (exec! actor "u2-scope" {:op :log-rental-record :asset-id "unit-2"
                              :out-of-scope? true :patch {}})

    (exec! actor "u3-log" {:op :log-rental-record :asset-id "unit-3"
                            :patch {:renter "unknown"}})

    (exec! actor "u1-log" {:op :log-rental-record :asset-id "unit-1"
                            :patch {:renter "Alex Kim" :checkout "2026-07-14" :days 3}})

    (exec! actor "u1-schedule" {:op :schedule-fleet-operation :asset-id "unit-1"
                                 :patch {:item "binding release-force calibration" :urgency "routine"}})

    (exec! actor "u1-restock-big" {:op :coordinate-fleet-restock :asset-id "unit-1"
                                    :patch {:item "replacement ski-set fleet order" :quantity 8 :estimated-cost 12000}})
    (approve! actor "u1-restock-big")

    (exec! actor "u2-restock" {:op :coordinate-fleet-restock :asset-id "unit-2"
                                :patch {:item "brake pad + cable set" :quantity 4 :estimated-cost 260}})

    (exec! actor "u2-safety" {:op :flag-equipment-safety-concern :asset-id "unit-2"
                               :patch {:concern "rear brake lever travel excessive after last return"
                                       :confidence 0.91}})
    (approve! actor "u2-safety")
    db))

;; ----------------------------- rendering (structure unchanged;
;; column labels + row extraction are domain-specific) -----------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger asset-id]
  (last (filter #(= (:asset-id %) asset-id) ledger)))

(defn- status-cell [ledger asset-id]
  (let [f (last-fact-for ledger asset-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- unit-row [ledger {:keys [asset-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc asset-id) (esc name)
          (cond
            (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
            registered? "<span class=\"warn\">registered, unverified</span>"
            :else "<span class=\"err\">unregistered</span>")
          (status-cell ledger asset-id)))

(defn- ledger-row [{:keys [t op asset-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc asset-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; `Ops` table, `recreationalrentalops.governor`/
  ;; `recreationalrentalops.phase`) -- documentation of fixed behavior,
  ;; not runtime telemetry, so it is legitimately hand-described rather
  ;; than derived from a live run.
  ["        <tr><td><code>:log-rental-record</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-fleet-operation</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-fleet-restock</code></td><td><span class=\"ok\">phase-3 auto when clean and under cost threshold</span> &middot; <span class=\"warn\">ALWAYS human approval above threshold</span></td></tr>"
   "        <tr><td><code>:flag-equipment-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        units (store/all-assets db)
        unit-rows (str/join "\n" (map (partial unit-row ledger) units))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-7721 &middot; recreational and sports goods rental operations</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Recreational and sports goods rental operations (ISIC 7721) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never finalizes equipment-safety clearance</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Rental fleet units</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>recreationalrentalops.store</code> via <code>recreationalrentalops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Unit</th><th>Name</th><th>Registration status</th><th>Last coordination status</th></tr></thead>\n"
     "      <tbody>\n"
     unit-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Recreational Rental Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Certifying a returned unit safe to re-rent without inspection, and overriding equipment-safety-authority decisions, are permanently out of scope — see governor scope-exclusion.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Unit</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
