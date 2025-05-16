package app.aaps.pump.tandem.common.database.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.pump.tandem.common.database.TandemPumpDatabase
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.database.data.entity.TandemQualifyingEventEntity
import io.reactivex.rxjava3.core.Single
import java.lang.System.currentTimeMillis
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbDataHandler @Inject constructor(
    val dbDataConverter: DbDataConverter,
    var tandemPumpDatabase: TandemPumpDatabase,
    val rxBus: RxBus,
    val aapsLogger: AAPSLogger,
    val aapsSchedulers: AapsSchedulers

) {

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


    fun addQualifyingEventRecords(
        listOfEvents: List<TandemQualifyingEventEntity>
    ) {

        tandemPumpDatabase.qualifyingEventsDao().saveAll(listOfEvents)
            .subscribeOn(aapsSchedulers.io)
            .observeOn(aapsSchedulers.main)
            .subscribe(
                { aapsLogger.error(LTag.PUMPCOMM, "Inserted QualifyingEvents: ${listOfEvents.size} ") },
                { error -> aapsLogger.error(LTag.PUMPCOMM, "Failed to insert QualifyingEvents: ${error.message}", error) }
            )

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