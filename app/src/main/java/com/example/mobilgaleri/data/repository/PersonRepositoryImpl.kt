//com/example/mobilgaleri/data/repository/PersonRepositoryImpl.kt
package com.example.mobilgaleri.data.repository

import com.example.mobilgaleri.data.local.dao.PersonDao
import com.example.mobilgaleri.data.local.entity.PersonEntity
import com.example.mobilgaleri.domain.repository.PersonRepository

class PersonRepositoryImpl(private val personDao: PersonDao) : PersonRepository {
    override suspend fun upsert(person: PersonEntity) {
        personDao.upsert(person)
    }

    override suspend fun getAllPersons(): List<PersonEntity> {
        return personDao.getAll()
    }

    override suspend fun deletePersons(persons: List<PersonEntity>) {
        personDao.deleteAll(persons)
    }

    // ARTIK HATA VERMEYECEK
    override suspend fun updateContactMatch(personId: Long, contactId: Long, confidence: Float) {
        personDao.updateContactMatch(personId, contactId, confidence)
    }
    override suspend fun updateContactName(personId: Long, contactName: String) {
        personDao.updateContactName(personId, contactName)
    }


}
