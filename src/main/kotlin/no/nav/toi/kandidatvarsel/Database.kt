package no.nav.toi.kandidatvarsel

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.lang.System.getenv
import javax.sql.DataSource
import kotlin.contracts.contract


data class DatabaseConfig(
    private val hostname: String,
    private val port: Int,
    private val database: String,
    private val username: String,
    private val password: String
) {
    fun createDataSource() = HikariDataSource().also {
        it.driverClassName = org.postgresql.Driver::class.qualifiedName
        it.jdbcUrl = "jdbc:postgresql://$hostname:$port/$database"
        it.username = username
        it.password = password
        it.maximumPoolSize = if (getenv("NAIS_CLUSTER_NAME") == "dev-gcp") 5 else 10
    }

    companion object {
        fun nais() = DatabaseConfig(
            /* Not using DB_URL, as it contains the password and some data sources might log it. */
            hostname = getenv("DB_HOST"),
            port = getenv("DB_PORT").toInt(),
            database = getenv("DB_DATABASE"),
            username = getenv("DB_USERNAME"),
            password = getenv("DB_PASSWORD"),
        )
    }
}

fun DataSource.isReady(timeout: Int = 1): Boolean = try {
    connection.use { it.isValid(timeout) }
} catch (e: Exception) {
    false
}

fun DataSource.migrate(): MigrateResult = Flyway.configure()
    .dataSource(this)
    .load()
    .migrate()


fun <T : Any?> DataSource.transaction(block: (JdbcClient) -> T): T {
    return connection.use { connection ->
        /* The built in way to have transactions with JdbcClient is to use
         * Spring's @Transactional-annotations. That technique introduces
         * proxy objects, with their own complexities and pittfals.
         */
        try {
            connection.autoCommit = false
            val singleConnectionDataSource = SingleConnectionDataSource(connection, true)
            val jdbcClient = JdbcClient.create(singleConnectionDataSource)
            val result = block(jdbcClient)
            connection.commit()
            return@use result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }
}