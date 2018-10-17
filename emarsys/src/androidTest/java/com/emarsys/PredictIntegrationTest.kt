package com.emarsys

import android.app.Application
import android.content.Context
import android.support.test.InstrumentationRegistry
import android.support.test.rule.ActivityTestRule
import com.emarsys.config.EmarsysConfig
import com.emarsys.core.DefaultCoreCompletionHandler
import com.emarsys.core.di.DependencyInjection
import com.emarsys.core.response.ResponseModel
import com.emarsys.di.DefaultEmarsysDependencyContainer
import com.emarsys.di.EmarysDependencyContainer
import com.emarsys.predict.api.model.PredictCartItem
import com.emarsys.predict.util.CartItemUtils
import com.emarsys.testUtil.ConnectionTestUtils
import com.emarsys.testUtil.ExperimentalTestUtils
import com.emarsys.testUtil.TimeoutUtils
import com.emarsys.testUtil.fake.FakeActivity
import io.kotlintest.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch

class PredictIntegrationTest {

    companion object {
        private const val APP_ID = "14C19-A121F"
        private const val APP_PASSWORD = "PaNkfOD90AVpYimMBuZopCpm8OWCrREu"
        private const val CONTACT_FIELD_ID = 3
        private const val MERCHANT_ID = "1428C8EE286EC34B"
    }

    private lateinit var latch: CountDownLatch
    private lateinit var baseConfig: EmarsysConfig
    private lateinit var responseModel: ResponseModel
    private lateinit var completionHandler: DefaultCoreCompletionHandler
    private lateinit var responseModelMatches: (ResponseModel) -> Boolean
    private var errorCause: Throwable? = null

    private val application: Application
        get() = InstrumentationRegistry.getTargetContext().applicationContext as Application

    @Rule
    @JvmField
    val timeout: TestRule = TimeoutUtils.timeoutRule

    @Rule
    @JvmField
    val activityRule = ActivityTestRule<FakeActivity>(FakeActivity::class.java)

    @Before
    fun setup() {
        application.getSharedPreferences("emarsys_shared_preferences", Context.MODE_PRIVATE).let {
            it.edit().clear().commit()
        }

        baseConfig = EmarsysConfig.Builder()
                .application(application)
                .mobileEngageCredentials(APP_ID, APP_PASSWORD)
                .contactFieldId(CONTACT_FIELD_ID)
                .predictMerchantId(MERCHANT_ID)
                .disableDefaultChannel()
                .build()

        latch = CountDownLatch(1)
        errorCause = null

        ConnectionTestUtils.checkConnection(application)
        ExperimentalTestUtils.resetExperimentalFeatures()

        completionHandler = object : DefaultCoreCompletionHandler(mutableListOf(), mutableMapOf()) {
            override fun onSuccess(id: String?, responseModel: ResponseModel) {
                super.onSuccess(id, responseModel)
                if (responseModel.isPredictRequest and this@PredictIntegrationTest.responseModelMatches(responseModel)) {
                    this@PredictIntegrationTest.responseModel = responseModel
                    latch.countDown()
                }
            }

            override fun onError(id: String?, cause: Exception) {
                super.onError(id, cause)
                this@PredictIntegrationTest.errorCause = cause
                latch.countDown()
            }

            override fun onError(id: String?, responseModel: ResponseModel) {
                super.onError(id, responseModel)
                this@PredictIntegrationTest.responseModel = responseModel
                latch.countDown()
            }
        }
        DependencyInjection.setup(object : DefaultEmarsysDependencyContainer(baseConfig) {
            override fun getCoreCompletionHandler() = completionHandler
        })

        Emarsys.setup(baseConfig)
    }

    @After
    fun tearDown() {
        with(DependencyInjection.getContainer<EmarysDependencyContainer>()) {
            application.unregisterActivityLifecycleCallbacks(activityLifecycleWatchdog)
            application.unregisterActivityLifecycleCallbacks(currentActivityWatchdog)
            coreSdkHandler.looper.quit()
        }

        DependencyInjection.tearDown()
    }

    @Test
    fun testTrackCart() {
        val cartItems = listOf(
                PredictCartItem("item1", 1.1, 10.0),
                PredictCartItem("item2", 2.2, 20.0),
                PredictCartItem("item3", 3.3, 30.0)
        )

        responseModelMatches = {
            it.baseUrl.contains(CartItemUtils.cartItemsToQueryParam(cartItems))
        }

        Emarsys.Predict.trackCart(cartItems)

        eventuallyAssertSuccess()
    }

    @Test
    fun testTrackPurchase() {
        val cartItems = listOf(
                PredictCartItem("item1", 1.1, 10.0),
                PredictCartItem("item2", 2.2, 20.0),
                PredictCartItem("item3", 3.3, 30.0)
        )

        val orderId = "orderId_1234567892345678"

        responseModelMatches = {
            it.baseUrl.contains(CartItemUtils.cartItemsToQueryParam(cartItems))
            it.baseUrl.contains(orderId)
        }

        Emarsys.Predict.trackPurchase(orderId, cartItems)

        eventuallyAssertSuccess()
    }

    @Test
    fun testTrackItemView() {
        val itemId = "itemId123456789"
        responseModelMatches = {
            it.baseUrl.contains(itemId)
        }

        Emarsys.Predict.trackItemView(itemId)

        eventuallyAssertSuccess()
    }

    @Test
    fun testTrackCategoryView() {
        val categoryId = "categoryId123456789"
        responseModelMatches = {
            it.baseUrl.contains(categoryId)
        }

        Emarsys.Predict.trackCategoryView(categoryId)

        eventuallyAssertSuccess()
    }

    @Test
    fun testTrackSearchTerm() {
        val searchTerm = "searchTerm123456789"
        responseModelMatches = {
            it.baseUrl.contains(searchTerm)
        }

        Emarsys.Predict.trackSearchTerm(searchTerm)

        eventuallyAssertSuccess()
    }

    @Test
    fun testMultipleInvocations() {
        testTrackCart()
        latch = CountDownLatch(1)
        testTrackPurchase()
        latch = CountDownLatch(1)
        testTrackCategoryView()
        latch = CountDownLatch(1)
        testTrackItemView()
        latch = CountDownLatch(1)
        testTrackSearchTerm()
    }

    private fun eventuallyAssertSuccess() {
        latch.await()
        errorCause shouldBe null
        responseModel.statusCode shouldBe 200
    }

    private val ResponseModel.isPredictRequest
        get() = this.requestModel.url.toString().startsWith("https://recommender.scarabresearch.com/merchants/$MERCHANT_ID?")

    private val ResponseModel.baseUrl
        get() = URLDecoder.decode(this.requestModel.url.toString(), "UTF-8")
}