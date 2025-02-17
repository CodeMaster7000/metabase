(ns metabase.api.automagic-dashboards-test
  (:require [clojure.test :refer :all]
            [metabase.automagic-dashboards.core :as magic]
            [metabase.models :refer [Card Collection Metric Segment]]
            [metabase.models.permissions :as perms]
            [metabase.models.permissions-group :as perms-group]
            [metabase.query-processor :as qp]
            [metabase.test :as mt]
            [metabase.test.automagic-dashboards :refer [with-dashboard-cleanup]]
            [metabase.test.domain-entities :as test.de]
            [metabase.test.fixtures :as fixtures]
            [metabase.test.transforms :as transforms.test]
            [metabase.transforms.core :as tf]
            [metabase.transforms.materialize :as tf.materialize]
            [metabase.transforms.specs :as tf.specs]
            [toucan.util.test :as tt]))

(use-fixtures :once (fixtures/initialize :db :web-server :test-users :test-users-personal-collections))

(defn- api-call
  ([template args]
   (api-call template args (constantly true)))

  ([template args revoke-fn]
   (api-call template args revoke-fn some?))

  ([template args revoke-fn validation-fn]
   (mt/with-test-user :rasta
     (with-dashboard-cleanup
       (let [api-endpoint (apply format (str "automagic-dashboards/" template) args)
             result       (validation-fn (mt/user-http-request :rasta :get 200 api-endpoint))]
         (when (and result
                    (try
                      (testing "Endpoint should return 403 if user does not have permissions"
                        (perms/revoke-data-perms! (perms-group/all-users) (mt/id))
                        (revoke-fn)
                        (let [result (mt/user-http-request :rasta :get 403 api-endpoint)]
                          (is (= "You don't have permissions to do that."
                                 result))))
                      (finally
                        (perms/grant-permissions! (perms-group/all-users) (perms/data-perms-path (mt/id))))))
           result))))))


;;; ------------------- X-ray  -------------------

(deftest table-xray-test
  (testing "GET /api/automagic-dashboards/table/:id"
    (is (some? (api-call "table/%s" [(mt/id :venues)]))))

  (testing "GET /api/automagic-dashboards/table/:id/rule/example/indepth"
    (is (some? (api-call "table/%s/rule/example/indepth" [(mt/id :venues)])))))

(deftest metric-xray-test
  (testing "GET /api/automagic-dashboards/metric/:id"
    (tt/with-temp Metric [{metric-id :id} {:table_id   (mt/id :venues)
                                           :definition {:query {:aggregation ["count"]}}}]
      (is (some? (api-call "metric/%s" [metric-id]))))))

(deftest segment-xray-test
  (tt/with-temp Segment [{segment-id :id} {:table_id   (mt/id :venues)
                                           :definition {:filter [:> [:field (mt/id :venues :price) nil] 10]}}]
    (testing "GET /api/automagic-dashboards/segment/:id"
      (is (some? (api-call "segment/%s" [segment-id]))))

    (testing "GET /api/automagic-dashboards/segment/:id/rule/example/indepth"
      (is (some? (api-call "segment/%s/rule/example/indepth" [segment-id]))))))


(deftest field-xray-test
  (testing "GET /api/automagic-dashboards/field/:id"
    (is (some? (api-call "field/%s" [(mt/id :venues :price)])))))

(defn- revoke-collection-permissions!
  [collection-id]
  (perms/revoke-collection-permissions! (perms-group/all-users) collection-id))

(deftest card-xray-test
  (mt/with-non-admin-groups-no-root-collection-perms
    (let [cell-query (#'magic/encode-base64-json [:> [:field (mt/id :venues :price) nil] 5])]
      (doseq [test-fn
              [(fn [collection-id card-id]
                 (testing "GET /api/automagic-dashboards/question/:id"
                   (is (some? (api-call "question/%s" [card-id] #(revoke-collection-permissions! collection-id))))))

               (fn [collection-id card-id]
                 (testing "GET /api/automagic-dashboards/question/:id/cell/:cell-query"
                   (is (some? (api-call "question/%s/cell/%s"
                                        [card-id cell-query]
                                        #(revoke-collection-permissions! collection-id))))))

               (fn [collection-id card-id]
                 (testing "GET /api/automagic-dashboards/question/:id/cell/:cell-query/rule/example/indepth"
                   (is (some? (api-call "question/%s/cell/%s/rule/example/indepth"
                                        [card-id cell-query]
                                        #(revoke-collection-permissions! collection-id))))))]]
        (tt/with-temp* [Collection [{collection-id :id}]
                        Card       [{card-id :id} {:table_id      (mt/id :venues)
                                                   :collection_id collection-id
                                                   :dataset_query (mt/mbql-query venues
                                                                    {:filter [:> $price 10]})}]]
          (perms/grant-collection-readwrite-permissions! (perms-group/all-users) collection-id)
          (test-fn collection-id card-id))))))

(deftest adhoc-query-xray-test
  (let [query (#'magic/encode-base64-json
               (mt/mbql-query venues
                 {:filter [:> $price 10]}))
        cell-query (#'magic/encode-base64-json
                    [:> [:field (mt/id :venues :price) nil] 5])]
    (testing "GET /api/automagic-dashboards/adhoc/:query"
      (is (some? (api-call "adhoc/%s" [query]))))

    (testing "GET /api/automagic-dashboards/adhoc/:query/cell/:cell-query"
      (is (some? (api-call "adhoc/%s/cell/%s" [query cell-query]))))

    (testing "GET /api/automagic-dashboards/adhoc/:query/cell/:cell-query/rule/example/indepth"
      (is (some? (api-call "adhoc/%s/cell/%s/rule/example/indepth" [query cell-query]))))))


;;; ------------------- Comparisons -------------------

(def ^:private segment
  (delay
    {:table_id   (mt/id :venues)
     :definition {:filter [:> [:field (mt/id :venues :price) nil] 10]}}))

(deftest comparisons-test
  (tt/with-temp Segment [{segment-id :id} @segment]
    (testing "GET /api/automagic-dashboards/table/:id/compare/segment/:segment-id"
      (is (some?
           (api-call "table/%s/compare/segment/%s"
                     [(mt/id :venues) segment-id]))))

    (testing "GET /api/automagic-dashboards/table/:id/rule/example/indepth/compare/segment/:segment-id"
      (is (some?
           (api-call "table/%s/rule/example/indepth/compare/segment/%s"
                     [(mt/id :venues) segment-id]))))

    (testing "GET /api/automagic-dashboards/adhoc/:id/cell/:cell-query/compare/segment/:segment-id"
      (is (some?
           (api-call "adhoc/%s/cell/%s/compare/segment/%s"
                     [(->> (mt/mbql-query venues
                             {:filter [:> $price 10]})
                           (#'magic/encode-base64-json))
                      (->> [:= [:field (mt/id :venues :price) nil] 15]
                           (#'magic/encode-base64-json))
                      segment-id]))))))

(deftest compare-nested-query-test
  (testing "Ad-hoc X-Rays should work for queries have Card source queries (#15655)"
    (mt/dataset sample-dataset
      (let [card-query      (mt/native-query {:query "select * from people"})
            result-metadata (get-in (qp/process-query card-query) [:data :results_metadata :columns]) ]
        (mt/with-temp* [Collection [{collection-id :id}]
                        Card       [{card-id :id} {:name            "15655_Q1"
                                                   :collection_id   collection-id
                                                   :dataset_query   card-query
                                                   :result_metadata result-metadata}]]
          (let [query      {:database (mt/id)
                            :type     :query
                            :query    {:source-table (format "card__%d" card-id)
                                       :breakout     [[:field "SOURCE" {:base-type :type/Text}]]
                                       :aggregation  [[:count]]}}
                cell-query [:= [:field "SOURCE" {:base-type :type/Text}] "Affiliate"]]
            (testing "X-Ray"
              (is (some? (api-call "adhoc/%s/cell/%s"
                                   (map #'magic/encode-base64-json [query cell-query])
                                   #(revoke-collection-permissions! collection-id)))))
            (perms/grant-collection-read-permissions! (perms-group/all-users) collection-id)
            (testing "Compare"
              (is (some? (api-call "adhoc/%s/cell/%s/compare/table/%s"
                                   (concat (map #'magic/encode-base64-json [query cell-query])
                                           [(format "card__%d" card-id)])
                                   #(revoke-collection-permissions! collection-id)))))))))))


;;; ------------------- Transforms -------------------

(deftest transforms-test
  (testing "GET /api/automagic-dashboards/transform/:id"
    (mt/with-test-user :rasta
      (transforms.test/with-test-transform-specs
        (test.de/with-test-domain-entity-specs
          (mt/with-model-cleanup [Card Collection]
            (tf/apply-transform! (mt/id) "PUBLIC" (first @tf.specs/transform-specs))
            (is (= [[1 "Red Medicine" 4 10.065 -165.374 3 1.5 4 3 2 1]
                    [2 "Stout Burgers & Beers" 11 34.1 -118.329 2 1.1 11 2 1 1]
                    [3 "The Apple Pan" 11 34.041 -118.428 2 1.1 11 2 1 1]]
                   (mt/formatted-rows [int str int 3.0 3.0 int 1.0 int int int int]
                     (api-call "transform/%s" ["Test transform"]
                               #(revoke-collection-permissions!
                                 (tf.materialize/get-collection "Test transform"))
                               (fn [dashboard]
                                 (->> dashboard
                                      :ordered_cards
                                      (sort-by (juxt :row :col))
                                      last
                                      :card
                                      :dataset_query
                                      qp/process-query))))))))))))
