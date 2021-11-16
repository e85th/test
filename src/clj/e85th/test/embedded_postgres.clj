(ns e85th.test.embedded-postgres
  (:require [com.stuartsierra.component :as component])
  (:import [javax.sql DataSource]
           [org.flywaydb.core Flyway]
           [org.flywaydb.core.api.configuration FluentConfiguration]
           [com.opentable.db.postgres.embedded DatabasePreparer PreparedDbProvider]))

(defn set-data-source
  [^FluentConfiguration config ^DataSource ds]
  (.dataSource config ds))

(defn migrate
  [^Flyway flyway]
  (.migrate flyway))

(defn set-locations
  "Sets the locations where flyway seaches for migrations.
   locations is a seq of strings signifying directories on disk."
  [^FluentConfiguration config locations]
  (.locations config (into-array locations)))


(defn set-placeholders
  [^FluentConfiguration config placeholder-map]
  (.placeholders config placeholder-map))

(defn set-filesystem-locations
  "Sets the filesystem locations where migration files are found.
   The locations should be `String` and not be prefixed with `filesystem:`.
   This function will handle that."
  [^FluentConfiguration config locations]
  (->> locations
       (map (partial str "filesystem:"))
       (set-locations config)))

(defn create-data-source
  [^PreparedDbProvider db-provider]
  (.createDataSource db-provider))

(defn database-preparer
  "Creates a new `DatabasePreparer` and returns the instance. The
  `pre-migrate-fn` and `post-migrate-fn` are single arity functions that
   take a `DataSource` and are called prior to and after the migrations
   have run."
  [^FluentConfiguration config pre-migrate-fn post-migrate-fn]
  (reify DatabasePreparer
    (prepare [this datasource]
      (pre-migrate-fn datasource)

      (-> config
          (set-data-source datasource)
          (.load)
          (.migrate))

      (post-migrate-fn datasource))))


(defn prepared-db-provider
  "Creates a new `PreparedDbProvider` which allows for migrations to be run once
  into Postgres' templateDB. Subsequent databases are generated from templateDB and
  are very fast."
  [^FluentConfiguration config pre-migrate-fn post-migrate-fn]
  (-> config
      (database-preparer pre-migrate-fn post-migrate-fn)
      PreparedDbProvider/forPreparer))

(defprotocol IFreshDataSource
  (fresh-data-source [this] "Creates and returns a brand new datasource."))

(defrecord Db [flyway-config pre-migrate-fn post-migrate-fn]
  component/Lifecycle
  (start [this]
    (if (:db-provider this)
      this
      (-> this
          (assoc :db-provider (prepared-db-provider flyway-config pre-migrate-fn post-migrate-fn))
          fresh-data-source)))

  (stop [this]
    (dissoc this :db-provider))

  IFreshDataSource
  (fresh-data-source [this]
    (assoc this :datasource (-> this :db-provider create-data-source))))


(def no-op (constantly nil))

(defn new-db
  "Creates a db with filesystem locations where migrations are found.
  See `database-preparer` for docs on `pre-migrate-fn` and `post-migrate-fn`."
  [{:keys [init-fn pre-migrate-fn post-migrate-fn
           config]
    :or {init-fn identity}}]
  (let [{:keys [placeholders locations]} config
        config (Flyway/configure)]
    (when locations (set-locations config locations))
    (when placeholders (set-placeholders config placeholders))
    (map->Db {:flyway-config (init-fn config)
              :pre-migrate-fn pre-migrate-fn
              :post-migrate-fn post-migrate-fn})))
