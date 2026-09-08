package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.ConverterViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PDF to EPUB Converter", appName)
  }

  @Test
  fun `test batch queue management`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val vm = ConverterViewModel(app)

    assertTrue(vm.batchQueue.value.isEmpty())

    val uri1 = Uri.parse("content://com.example/document/1")
    val uri2 = Uri.parse("content://com.example/document/2")

    vm.addBatchFiles(listOf(uri1, uri2))
    assertEquals(2, vm.batchQueue.value.size)

    vm.removeBatchItemAt(0)
    assertEquals(1, vm.batchQueue.value.size)

    vm.clearBatch()
    assertTrue(vm.batchQueue.value.isEmpty())
  }
}
