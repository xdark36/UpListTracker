package com.example.uplisttracker.di

import com.example.uplisttracker.PositionRepository
import com.example.uplisttracker.PositionUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun providePositionRepository(): PositionRepository = PositionRepository

    @Provides
    @Singleton
    fun providePositionUtils(): PositionUtils = PositionUtils
} 