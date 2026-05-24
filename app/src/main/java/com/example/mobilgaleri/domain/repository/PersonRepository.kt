//com/example/mobilgaleri/domain/repository/PersonRepository.kt
package com.example.mobilgaleri.domain.repository

import com.example.mobilgaleri.data.local.entity.PersonEntity

interface PersonRepository {
    suspend fun upsert(person: PersonEntity)
    suspend fun updateContactMatch(personId: Long, contactId: Long, confidence: Float) // <-- BU SATIRI EKLE
    suspend fun getAllPersons(): List<PersonEntity>
    suspend fun deletePersons(persons: List<PersonEntity>)
    suspend fun updateContactName(personId: Long, contactName: String)

}
