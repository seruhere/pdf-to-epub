package com.example.data.repository

import com.example.data.db.BookDao
import com.example.data.model.ConvertedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<ConvertedBook>> = bookDao.getAllBooks()

    fun getBookById(id: Long): Flow<ConvertedBook?> = bookDao.getBookById(id)

    fun searchBooks(query: String): Flow<List<ConvertedBook>> = bookDao.searchBooks(query)

    suspend fun saveBook(book: ConvertedBook): Long = withContext(Dispatchers.IO) {
        bookDao.insertBook(book)
    }

    suspend fun deleteBook(book: ConvertedBook) = withContext(Dispatchers.IO) {
        // Delete generated EPUB file
        try {
            val epubFile = File(book.epubFilePath)
            if (epubFile.exists()) {
                epubFile.delete()
            }
        } catch (_: Exception) {}

        // Delete generated cover file if exists
        book.coverImagePath?.let { path ->
            try {
                val coverFile = File(path)
                if (coverFile.exists()) {
                    coverFile.delete()
                }
            } catch (_: Exception) {}
        }

        bookDao.deleteBook(book)
    }

    suspend fun updateReadingProgress(bookId: Long, chapterIndex: Int, offset: Int) = withContext(Dispatchers.IO) {
        bookDao.updateReadingProgress(bookId, chapterIndex, offset)
    }

    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        bookDao.toggleFavorite(bookId, isFavorite)
    }

    suspend fun updateBook(book: ConvertedBook) = withContext(Dispatchers.IO) {
        bookDao.updateBook(book)
    }
}
