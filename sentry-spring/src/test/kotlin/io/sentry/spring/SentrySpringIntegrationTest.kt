package io.sentry.spring

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.test.checkEvent
import io.sentry.transport.ITransport
import java.lang.RuntimeException
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RunWith(SpringRunner::class)
@SpringBootTest(
    classes = [App::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SentrySpringIntegrationTest {

    @Autowired
    lateinit var transport: ITransport

    @LocalServerPort
    lateinit var port: Integer

    @Before
    fun `reset mocks`() {
        reset(transport)
    }

    @Test
    fun `attaches request and user information to SentryEvents`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders()
        headers["X-FORWARDED-FOR"] = listOf("169.128.0.1")
        val entity = HttpEntity<Void>(headers)

        restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.request).isNotNull()
                assertThat(event.request!!.url).isEqualTo("http://localhost:$port/hello")
                assertThat(event.user).isNotNull()
                assertThat(event.user.username).isEqualTo("user")
                assertThat(event.user.ipAddress).isEqualTo("169.128.0.1")
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches first ip address if multiple addresses exist in a header`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders()
        headers["X-FORWARDED-FOR"] = listOf("169.128.0.1, 192.168.0.1")
        val entity = HttpEntity<Void>(headers)

        restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.user.ipAddress).isEqualTo("169.128.0.1")
            }, anyOrNull())
        }
    }

    @Test
    fun `sends events for unhandled exceptions`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.exceptions).isNotEmpty
                val ex = event.exceptions.first()
                assertThat(ex.value).isEqualTo("something went wrong")
                assertThat(ex.mechanism.isHandled).isFalse()
            }, anyOrNull())
        }
    }

    @Test
    fun `attaches transaction name to events`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

        await.untilAsserted {
            verify(transport).send(checkEvent { event ->
                assertThat(event.transaction).isEqualTo("GET /throws")
            }, anyOrNull())
        }
    }

    @Test
    fun `does not send events for handled exceptions`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws-handled", String::class.java)

        await.during(Duration.ofSeconds(2)).untilAsserted {
            verifyZeroInteractions(transport)
        }
    }
}

@SpringBootApplication
@EnableSentry(dsn = "http://key@localhost/proj", sendDefaultPii = true)
open class App {

    private val transport = mock<ITransport>()

    @Bean
    open fun mockTransportFactory(): ITransportFactory {
        val factory = mock<ITransportFactory>()
        whenever(factory.create(any(), any())).thenReturn(transport)
        return factory
    }

    @Bean
    open fun mockTransport() = transport

    @Bean
    open fun sentrySpringRequestListener() = SentrySpringRequestListener()
}

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello() {
        Sentry.captureMessage("hello")
    }

    @GetMapping("/throws")
    fun throws() {
        throw RuntimeException("something went wrong")
    }

    @GetMapping("/throws-handled")
    fun throwsHandled() {
        throw CustomException("handled exception")
    }
}

class CustomException(message: String) : RuntimeException(message)

@ControllerAdvice
class ExceptionHandlers {

    @ExceptionHandler(CustomException::class)
    fun handle(e: CustomException) = ResponseEntity.badRequest().build<Void>()
}

@Configuration
open class SecurityConfiguration : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.csrf().disable()
            .authorizeRequests().anyRequest().authenticated()
            .and()
            .httpBasic()
    }

    @Bean
    override fun userDetailsService(): UserDetailsService {
        val encoder: PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val user: UserDetails = User
            .builder()
            .passwordEncoder { rawPassword -> encoder.encode(rawPassword) }
            .username("user")
            .password("password")
            .roles("USER")
            .build()
        return InMemoryUserDetailsManager(user)
    }
}
