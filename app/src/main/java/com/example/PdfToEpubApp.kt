package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.BookRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfToEpubApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: BookRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox resource loader for Android
        PDFBoxResourceLoader.init(applicationContext)

        // Initialize Room database & repository
        database = AppDatabase.getInstance(this)
        repository = BookRepository(database.bookDao())
    }
}
