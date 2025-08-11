package app.aaps.pump.tandem.common.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single

// TODO TandemHistoryRecordDao refactor this for Tandem  N-8

@Dao
abstract class TandemHistoryRecordDao {

    // @Query("SELECT * from history_records")
    // abstract fun all(): Single<List<TandemHistoryRecordEntity>>
    //
    // @Query("SELECT * from history_records order by sequenceId desc")
    // abstract fun allBlocking(): List<TandemHistoryRecordEntity>
    //
    // @Query("SELECT * from history_records WHERE pumpTime >= :since")
    // abstract fun allSince(since: Long): Single<List<TandemHistoryRecordEntity>>
    //
    // @Query("SELECT * from history_records WHERE pumpTime >= :since order by sequenceId desc")
    // abstract fun allSinceBlocking(since: Long): List<TandemHistoryRecordEntity>

    @Query("SELECT * from history_records WHERE pumpSerial=:serialNo AND pumpTime >= :since order by sequenceId desc")
    abstract fun allSinceWithSerial(serialNo: Int, since: Long): Single<List<TandemHistoryRecordEntity>>

    @Query("SELECT * from history_records WHERE pumpSerial=:serialNo AND pumpTime >= :since order by sequenceId desc")
    abstract fun allSinceWithSerialBlocking(serialNo: Int, since: Long): List<TandemHistoryRecordEntity>

    @Query("SELECT COUNT(*) FROM history_records")
    abstract fun getHistoryCount(): Single<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveBlocking(tandemHistoryRecordEntity: TandemHistoryRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveAll(list: List<TandemHistoryRecordEntity>): Completable

    // @Query("SELECT * from history_records where id = :id and serial= :serialNumber and entryType= :entryType")
    // abstract fun getById(id: Int, serialNumber: Long, entryType: HistoryEntryType): HistoryRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun save(tandemHistoryRecordEntity: TandemHistoryRecordEntity): Completable

    // @Update(onConflict = OnConflictStrategy.REPLACE)
    // abstract fun update(tandemHistoryRecordEntity: TandemHistoryRecordEntity): Completable
    //
    // @Update(onConflict = OnConflictStrategy.REPLACE)
    // abstract fun updateBlocking(tandemHistoryRecordEntity: TandemHistoryRecordEntity)

    @Delete
    abstract fun delete(tandemHistoryRecordEntity: TandemHistoryRecordEntity): Completable

    // @Query(
    //     "SELECT * from history_records where serial = :serialNumber and historyRecordType= :entryType " +
    //         " and id= (select max(id) from history_records where serial = :serialNumber " +
    //         " and historyRecordType= :entryType) "
    // )
    // abstract fun getLatestHistoryEntry(serialNumber: Long, entryType: HistoryEntryType): HistoryRecordEntity?

    // @Query(
    //     "SELECT * from history_records where serial = :serialNumber and historyRecordType='Event' " +
    //     " and id= ( select max(id) from history_records where serial = :serialNumber " +
    //     "           and historyRecordType='Event' " +
    //     "           and (entryType='PUMP_MODE_CHANGED' or entryType='DELIVERY_STATUS_CHANGED')) ")
    // abstract fun getLatestDeliveryStatusChanged(serialNumber: Long): HistoryRecordEntity?

    //PUMP_MODE_CHANGED, DELIVERY_STATUS_CHANGED

    // select * from test_table where type = 'EVENT' and number_count =
    // (select max(test_table.number_count) from test_table where type = 'EVENT')

    // @Query("UPDATE historyrecords SET resolvedResult = :resolvedResult, resolvedAt = :resolvedAt WHERE id = :id ")
    // abstract fun markResolved(id: String, resolvedResult: ResolvedResult, resolvedAt: Long): Completable





}