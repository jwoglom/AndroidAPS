package app.aaps.pump.tandem.common.database

import com.google.gson.Gson


import app.aaps.pump.tandem.common.util.TandemPumpUtil
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

import app.aaps.pump.tandem.common.database.dao.TandemHistoryRecordDao
import app.aaps.pump.tandem.common.database.data.DbDataConverter
import app.aaps.pump.tandem.common.database.data.dto.TandemHistoryRecordDto
import app.aaps.pump.tandem.common.database.data.entity.TandemHistoryRecordEntity
import app.aaps.pump.tandem.common.driver.TandemPumpStatus
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import io.reactivex.rxjava3.core.Single
import java.lang.System.currentTimeMillis
import java.util.*
//import javax.inject.Inject

// TODO TandemPumpHistory refactor this for Tandem N-3
class TandemPumpHistory /*@Inject*/ constructor(
    val pumpHistoryDao: TandemHistoryRecordDao,
    val pumpHistoryDatabase: TandemPumpDatabase,
    //val historyMapper: HistoryMapper,
    val dbDataConverter: DbDataConverter,
    //val pumpSync: PumpSync,
    val pumpUtil: TandemPumpUtil,
    val pumpStatus: TandemPumpStatus,
    val aapsLogger: AAPSLogger
) {

    var gson: Gson = pumpUtil.gson
    var prefix: String = "DB: "

    //fun markSuccess(id: String, date: Long): Completable = dao.markResolved(id, ResolvedResult.SUCCESS, currentTimeMillis())

    //fun markFailure(id: String, date: Long): Completable = dao.markResolved(id, ResolvedResult.FAILURE, currentTimeMillis())

    fun createRecord(
        sequenceId: Long,
        pumpSerial: Int,
        typeId: Int,
        pumpTime: Long,   // EpochInMillis (pump stores time as EpochSeconds from Jan2008, we don't)
        payload: ByteArray,
        entitySubId: Int? = null, // some entities have special id (for example TBR has tempRateId)


        // serial: Long,
        // historyType: TandemPumpHistoryType,
        // historyTypeIndex: Int,
        // sequenceNum: Long,
        // dateTimeMillis: Long,
        // payload: String,
        // tempBasalRecord: TemporaryBasal?,
        // bolusRecord: Bolus?,
        // tdiRecord: TotalDailyInsulin?,
        // basalProfileRecord: BasalProfile?,
        // alarmRecord: Alarm?,
        // configRecord: ConfigurationChanged?,
        // pumpStatusRecord: PumpStatusChanged?,
        //dateTimeRecord: DateTimeChanged?
    ): Single<Long> {

        // var id = sequenceId

        return pumpHistoryDatabase.historyRecordDao().save(
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

    // fun addRecord(eventDto: EventDto) : Single<Long> {
    //     val entity = historyMapper.domainToEntity(eventDto)
    //     entity.id = entity.eventSequenceNumber
    //     entity.createdAt = currentTimeMillis()
    //     entity.updatedAt = currentTimeMillis()
    //
    //
    //
    //     return pumpHistoryDao.save(entity).toSingle { entity.id }
    // }

    fun getRecords(): Single<List<TandemHistoryRecordDto>> =
        pumpHistoryDao.all().map { list -> listOf<TandemHistoryRecordDto>()
            list.map(dbDataConverter::getHistoryRecordDto)
        }

    fun getRecordsAfter(time: Long): Single<List<TandemHistoryRecordEntity>> =
        pumpHistoryDatabase.historyRecordDao().allSince(time)

    // fun processList(entityList: List<HistoryRecordEntity>) {
    //     var first = true
    //     for (historyRecordEntity in entityList) {
    //         insertOrUpdate(historyRecordEntity)
    //     }
    // }

    fun getHistoryRecords(): List<TandemHistoryRecordEntity> {
        val history = pumpHistoryDatabase.historyRecordDao().all().blockingGet()
        aapsLogger.info(LTag.PUMP, "History entries: ${history.size}")
        return history
    }

    fun getHistoryRecordsAfter(atdTime: Long): List<TandemHistoryRecordEntity> {
        return pumpHistoryDatabase.historyRecordDao().allSince(atdTime).blockingGet()
    }

    fun insertOrUpdate(event: HistoryLog): TandemHistoryRecordEntity? {
        // aapsLogger.debug(LTag.PUMP, prefix + "EventDto to convert = ${gson.toJson(event)}")
        // val entity = historyMapper.domainToEntity(event)
        // var returnEntity: HistoryRecordEntity? = null
        // pumpHistoryDatabase.runInTransaction {
        //     val dbEntity = pumpHistoryDao.getById(entity.id, entity.serial, entity.historyRecordType)
        //
        //     aapsLogger.debug(LTag.PUMP, prefix + "pumpHistoryDao.getById[${entity.id}] = ${gson.toJson(dbEntity)}")
        //
        //     if (dbEntity == null) {
        //         entity.id = entity.eventSequenceNumber
        //         entity.createdAt = System.currentTimeMillis()
        //         entity.updatedAt = entity.createdAt
        //
        //         aapsLogger.debug(LTag.PUMP, prefix + "pumpHistoryDao.saveBlocking()")
        //
        //         pumpHistoryDao.saveBlocking(entity)
        //         returnEntity = entity
        //     } else {
        //         if (isDifferentData(dbEntity, entity)) {
        //             val entityForUpdate = prepareData(dbEntity, entity)
        //             aapsLogger.debug(LTag.PUMP, prefix + "pumpHistoryDao.updateBlocking()")
        //             pumpHistoryDao.updateBlocking(entityForUpdate)
        //             returnEntity = entityForUpdate
        //         } else {
        //             aapsLogger.debug(LTag.PUMP, prefix + "same data no Db action.")
        //         }
        //     }
        // }
        //
        // return returnEntity
        //
        // // if (returnEntity != null) {
        // //     //sendDataToPumpSync(returnEntity!!)
        // // }

        TODO()
        return null

    }

    private fun prepareData(dbEntity: TandemHistoryRecordEntity, newEntity: TandemHistoryRecordEntity): TandemHistoryRecordEntity {
        // TODO prepareData  N-3
        TODO()

        // if (dbEntity.entryType != newEntity.entryType)
        //     dbEntity.entryType = newEntity.entryType
        //
        // if (dbEntity.entryTypeAsInt != newEntity.entryTypeAsInt)
        //     dbEntity.entryTypeAsInt = newEntity.entryTypeAsInt
        //
        // if (dbEntity.value1 != newEntity.value1)
        //     dbEntity.value1 != newEntity.value1
        //
        // if (dbEntity.value2 != newEntity.value2)
        //     dbEntity.value2 != newEntity.value2
        //
        // if (dbEntity.value3 != newEntity.value3)
        //     dbEntity.value3 != newEntity.value3
        //
        // if (dbEntity.date != newEntity.date)
        //     dbEntity.date != newEntity.date
        //
        //
        // var id: Long,
        // var serial: Long,
        // var pumpEventType: TandemPumpEventType,
        // var sequenceNum: Long,
        // var dateTimeMillis: Long,

        // if (!Objects.equals(dbEntity.bolusRecord, newEntity.bolusRecord))
        //     dbEntity.bolusRecord = newEntity.bolusRecord
        //
        // if (!Objects.equals(dbEntity.temporaryBasalRecord, newEntity.temporaryBasalRecord))
        //     dbEntity.temporaryBasalRecord = newEntity.temporaryBasalRecord
        //
        // if (!Objects.equals(dbEntity.tddRecord, newEntity.tddRecord))
        //     dbEntity.tddRecord = newEntity.tddRecord
        //
        // if (!Objects.equals(dbEntity.basalProfileRecord, newEntity.basalProfileRecord))
        //     dbEntity.basalProfileRecord = newEntity.basalProfileRecord
        //
        // if (!Objects.equals(dbEntity.alarmRecord, newEntity.alarmRecord))
        //     dbEntity.alarmRecord = newEntity.alarmRecord
        //
        // if (!Objects.equals(dbEntity.configRecord, newEntity.configRecord))
        //     dbEntity.configRecord = newEntity.configRecord
        //
        // if (!Objects.equals(dbEntity.pumpStatusRecord, newEntity.pumpStatusRecord))
        //     dbEntity.pumpStatusRecord = newEntity.pumpStatusRecord

        // if (!Objects.equals(dbEntity.pumpTime, newEntity.pumpTime))
        //     dbEntity.pumpTime = newEntity.pumpTime

        dbEntity.updatedAt = System.currentTimeMillis()

        return dbEntity
    }

    private fun isDifferentData(entity1: TandemHistoryRecordEntity, entity2: TandemHistoryRecordEntity): Boolean {
        // TODO isDifferentData N-3
        // if (entity1.entryType != entity2.entryType ||
        //     entity1.entryTypeAsInt != entity2.entryTypeAsInt ||
        //     entity1.value1 != entity2.value1 ||
        //     entity1.value2 != entity2.value2 ||
        //     entity1.value3 != entity2.value3 ||
        //     entity1.date != entity2.date)
        //     return true

        // if (!Objects.equals(entity1.bolusRecord, entity2.bolusRecord) ||
        //     !Objects.equals(entity1.temporaryBasalRecord, entity2.temporaryBasalRecord) ||
        //     !Objects.equals(entity1.tddRecord, entity2.tddRecord) ||
        //     !Objects.equals(entity1.basalProfileRecord, entity2.basalProfileRecord) ||
        //     !Objects.equals(entity1.alarmRecord, entity2.alarmRecord) ||
        //     !Objects.equals(entity1.configRecord, entity2.configRecord) ||
        //     !Objects.equals(entity1.pumpStatusRecord, entity2.pumpStatusRecord) ||
        //     !Objects.equals(entity1.dateTimeRecord, entity2.dateTimeRecord))
        //     return true

        return false

    }

    // fun getLatestHistoryEntry(serialNumber: Long, entryType: HistoryEntryType): HistoryRecordEntity? {
    //     return pumpHistoryDao.getLatestHistoryEntry(serialNumber, entryType)
    // }

    // fun getLatestDeliveryStatusChangedEntry(): HistoryRecordEntity? {
    //     return pumpHistoryDao.getLatestDeliveryStatusChanged(pumpStatus.serialNumber!!)
    // }

}