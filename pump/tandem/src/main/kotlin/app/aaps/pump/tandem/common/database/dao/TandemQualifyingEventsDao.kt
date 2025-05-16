package app.aaps.pump.tandem.common.database.dao

import androidx.room.*
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

@Dao
abstract class TandemQualifyingEventsDao {

    @Query("SELECT * from qualifying_events")
    abstract fun all(): Single<List<TandemQualifyingEventEntity>>

    @Query("SELECT * from qualifying_events order by id desc")
    abstract fun allBlocking(): List<TandemQualifyingEventEntity>

    @Query("SELECT * from qualifying_events WHERE dateTime >= :since")
    abstract fun allSince(since: Long): Single<List<TandemQualifyingEventEntity>>

    @Query("SELECT * from qualifying_events WHERE dateTime >= :since order by id desc")
    abstract fun allSinceBlocking(since: Long): List<TandemQualifyingEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveBlocking(tandemHistoryRecordEntity: TandemQualifyingEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun save(tandemHistoryRecordEntity: TandemQualifyingEventEntity): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveAll(list: List<TandemQualifyingEventEntity>): Completable

    @Delete
    abstract fun delete(tandemHistoryRecordEntity: TandemQualifyingEventEntity): Completable

    @Query("SELECT COUNT(*) FROM qualifying_events")
    abstract fun getEventsCount(): Single<Long>


}