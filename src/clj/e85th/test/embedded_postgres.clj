(ns e85th.test.embedded-postgres
  (:require [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component])
  (:import [javax.sql DataSource]
           [org.flywaydb.core Flyway]
           [com.opentable.db.postgres.embedded DatabasePreparer PreparedDbProvider]))

(defn set-data-source
  [^Flyway flyway ^DataSource ds]
  (.setDataSource flyway ds))

(defn migrate
  [^Flyway flyway]
  (.migrate flyway))

(defn set-locations
  "Sets the locations where flyway seaches for migrations.
   locations is a seq of strings signifying directories on disk."
  [^Flyway flyway locations]
  (.setLocations flyway (into-array locations)))


(defn set-placeholders
  [^Flyway flyway placeholder-map]
  (.setPlaceholders flyway placeholder-map))

(defn set-filesystem-locations
  "Sets the filesystem locations where migration files are found.
   The locations should be `String` and not be prefixed with `filesystem:`.
   This function will handle that."
  [^Flyway flyway locations]
  (->> locations
       (map (partial str "filesystem:"))
       (set-locations flyway)))

(defn create-data-source
  [^PreparedDbProvider db-provider]
  (.createDataSource db-provider))

(defn database-preparer
  "Creates a new `DatabasePreparer` and returns the instance. The
  `pre-migrate-fn` and `post-migrate-fn` are single arity functions that
   take a `DataSource` and are called prior to and after the migrations
   have run."
  [^Flyway flyway pre-migrate-fn post-migrate-fn]
  (reify DatabasePreparer
    (prepare [this datasource]
      (pre-migrate-fn datasource)

      (doto flyway
        (set-data-source datasource)
        (migrate))

      (post-migrate-fn datasource))))


(defn prepared-db-provider
  "Creates a new `PreparedDbProvider` which allows for migrations to be run once
  into Postgres' templateDB. Subsequent databases are generated from templateDB and
  are very fast."
  [^Flyway flyway pre-migrate-fn post-migrate-fn]
  (-> flyway
      (database-preparer pre-migrate-fn post-migrate-fn)
      PreparedDbProvider/forPreparer))

(defprotocol IFreshDataSource
  (fresh-data-source [this] "Creates and returns a brand new datasource."))

(defrecord Db [flyway pre-migrate-fn post-migrate-fn]
  component/Lifecycle
  (start [this]
    (if (:db-provider this)
      this
      (-> this
          (assoc :db-provider (prepared-db-provider flyway pre-migrate-fn post-migrate-fn))
          fresh-data-source)))

  (stop [this]
    (dissoc this :db-provider))

  IFreshDataSource
  (fresh-data-source [this]
    (assoc this :datasource (-> this :db-provider create-data-source))))


(defn new-db
  "Creates a db with filesystem locations where migrations are found.
  See `database-preparer` for docs on `pre-migrate-fn` and `post-migrate-fn`."
  ([flyway-init-fn]
   (new-db flyway-init-fn (constantly nil) (constantly nil)))
  ([flyway-init-fn pre-migrate-fn]
   (new-db flyway-init-fn pre-migrate-fn (constantly nil)))
  ([flyway-init-fn pre-migrate-fn post-migrate-fn]
   (map->Db {:flyway (flyway-init-fn (Flyway.))
             :pre-migrate-fn pre-migrate-fn
             :post-migrate-fn post-migrate-fn})))
