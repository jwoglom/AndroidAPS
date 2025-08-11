package app.aaps.pump.tandem.common.database.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.tandem.common.comm.qe.QualifyingEventHandler
import app.aaps.pump.tandem.common.data.defs.QualifyingEventsRange
import app.aaps.pump.tandem.common.database.TandemPumpDatabase
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import app.aaps.pump.tandem.common.keys.TandemStringPreferenceKey
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import io.reactivex.rxjava3.core.Single
import java.lang.System.currentTimeMillis
import java.util.GregorianCalendar
import java.util.stream.Collectors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbDataHandler @Inject constructor(
    val dbDataConverter: DbDataConverter,
    var tandemPumpDatabase: TandemPumpDatabase,
    val rxBus: RxBus,
    val aapsLogger: AAPSLogger,
    val pumpStatus: TandemPumpStatus,
    val aapsSchedulers: AapsSchedulers,
    val preferences: Preferences,
    val qualifyingEventHandler: QualifyingEventHandler

) {

    val TAG = LTag.PUMPCOMM

    fun createHistoryRecord(
        sequenceId: Long,
        pumpSerial: Int,
        typeId: Int,
        pumpTime: Long,   // EpochInMillis (pump stores time as EpochSeconds from Jan2008, we don't)
        payload: ByteArray,
        entitySubId: Int? = null, // some entities have special id (for example TBR has tempRateId)
    ): Single<Long> {

        // var id = sequenceId

        return tandemPumpDatabase.historyRecordDao().save(
            TandemHistoryRecordEntity(
                sequenceId = sequenceId,
                pumpSerial = pumpSerial,
                typeId = typeId,
                pumpTime = pumpTime,
                payload = payload,
                entitySubId = entitySubId,
                createdAt = currentTimeMillis(),
                updatedAt = currentTimeMillis()
            )
        ).toSingle { sequenceId }
    }


    fun createQualifyingEventRecord(
        pumpSerial: Int,
        dateTime: Long,
        name: String,
        description: String? = null, // some entities have special id (for example TBR has tempRateId)
    ): Single<Long> {

        var id :Long  = 0

        return tandemPumpDatabase.qualifyingEventsDao().save(
            TandemQualifyingEventEntity(
                pumpSerial = pumpSerial,
                dateTime = dateTime,
                name = name,
                description = description
            )
        ).toSingle { id }

    }


    fun addHistoryRecords(listOfHistoryEntries: List<TandemHistoryRecordEntity>) {
        tandemPumpDatabase.historyRecordDao().saveAll(listOfHistoryEntries)


    }

    fun addHistoryLogs(listOfHistoryEntries: MutableCollection<HistoryLog>) {

        val entities = listOfHistoryEntries.stream()
            .map { item -> dbDataConverter.getTandemHistoryRecordEntity(item) }
            .collect(Collectors.toList())

        tandemPumpDatabase.historyRecordDao().saveAll(entities)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { aapsLogger.error(TAG, "Inserted TandemHistoryRecordEntity/HistoryLog: ${entities.size} ") },
                { error -> aapsLogger.error(TAG, "Failed to insert TandemHistoryRecordEntity: ${error.message}", error) }
            )
    }


    fun addQualifyingEventRecords(
        listOfEvents: List<TandemQualifyingEventEntity>
    ) {

        tandemPumpDatabase.qualifyingEventsDao().saveAll(listOfEvents)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { aapsLogger.error(TAG, "Inserted QualifyingEvents: ${listOfEvents.size} ") },
                { error -> aapsLogger.error(TAG, "Failed to insert QualifyingEvents: ${error.message}", error) }
            )

    }

    fun getCurrentQEItemsBlocking(): List<TandemQualifyingEventEntity> {

        val qeRange = QualifyingEventsRange.valueOf(preferences.get(TandemStringPreferenceKey.QualifyingEventsRangePref))

        aapsLogger.info(TAG, "Get Current QualifyingEvents Items with range: ${qeRange.name}")

        var itemLimit = 0

        var qeItems = if (qeRange==QualifyingEventsRange.LAST_15_ITEMS) {
            itemLimit = 15
            tandemPumpDatabase.qualifyingEventsDao().getLast30ItemsWithSerialBlocking(pumpStatus.serialNumber.toInt())
        } else {
            val gcNow = GregorianCalendar()
            when (qeRange) {
                QualifyingEventsRange.LAST_3_HOURS  -> gcNow.add(GregorianCalendar.HOUR_OF_DAY, -3)
                QualifyingEventsRange.LAST_6_HOURS  -> gcNow.add(GregorianCalendar.HOUR_OF_DAY, -6)
                QualifyingEventsRange.LAST_12_HOURS -> gcNow.add(GregorianCalendar.HOUR_OF_DAY, -12)
                QualifyingEventsRange.LAST_24_HOURS -> gcNow.add(GregorianCalendar.HOUR_OF_DAY, -24)
                QualifyingEventsRange.LAST_15_ITEMS -> { }
            }
            tandemPumpDatabase.qualifyingEventsDao().allSinceWithSerialBlocking(pumpStatus.serialNumber.toInt(), gcNow.timeInMillis)
        }

        qeItems = qualifyingEventHandler.filterQualifyingEventsAndLimit(qeItems, itemLimit)

        return qeItems
    }



    fun databaseStatistics() {

        aapsLogger.error(LTag.PUMPCOMM, "\n" +
            "Database Statistics - Tandem Database\n" +
            "======================================\n")

        tandemPumpDatabase.qualifyingEventsDao().getEventsCount()
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe { count: Long ->
                aapsLogger.error(LTag.PUMPCOMM, " Qualifying Events - $count") }

        tandemPumpDatabase.historyRecordDao().getHistoryCount()
        .subscribeOn(aapsSchedulers.io)
        .observeOn(aapsSchedulers.main)
        .subscribe { count: Long ->
            aapsLogger.error(LTag.PUMPCOMM, " History Records - $count") }

    }



}