package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ConvertedBook
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM converted_books ORDER BY convertedAtMillis DESC")
    fun getAllBooks(): Flow<List<ConvertedBook>>

    @Query("SELECT * FROM converted_books WHERE id = :id")
    fun getBookById(id: Long): Flow<ConvertedBook?>

    @Query("SELECT * FROM converted_books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' ORDER BY convertedAtMillis DESC")
    fun searchBooks(query: String): Flow<List<ConvertedBook>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: ConvertedBook): Long

    @Update
    suspend fun updateBook(book: ConvertedBook)

    @Delete
    suspend fun deleteBook(book: ConvertedBook)

    @Query("DELETE FROM converted_books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("UPDATE converted_books SET lastReadChapterIndex = :chapterIndex, lastReadScrollOffset = :offset WHERE id = :bookId")
    suspend fun updateReadingProgress(bookId: Long, chapterIndex: Int, offset: Int)

    @Query("UPDATE converted_books SET isFavorite = :isFavorite WHERE id = :bookId")
    suspend fun toggleFavorite(bookId: Long, isFavorite: Boolean)
}
