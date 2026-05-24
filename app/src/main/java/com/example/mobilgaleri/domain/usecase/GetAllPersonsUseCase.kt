package com.example.mobilgaleri.domain.usecase

import com.example.mobilgaleri.domain.repository.PersonRepository
import com.example.mobilgaleri.data.local.entity.PersonEntity

class GetAllPersonsUseCase(
    private val repo: PersonRepository
) {
    suspend operator fun invoke(): List<PersonEntity> {
        return repo.getAllPersons()
    }
}
