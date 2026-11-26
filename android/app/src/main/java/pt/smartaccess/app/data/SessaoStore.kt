// Guarda a sessao (token + utilizador) no telemovel, para nao ser preciso fazer login sempre.
package pt.smartaccess.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Ficheiro de preferencias 'sessao', privado da app (sobrevive a fechar a app)
private val Context.dataStore by preferencesDataStore(name = "sessao")

// O que fica guardado: token JWT e o utilizador
data class DadosSessao(val token: String, val utilizador: Utilizador)

// Guarda o token e o utilizador autenticado entre arranques da app
class SessaoStore(private val context: Context) {

    // Emite a sessao guardada sempre que muda (null se nao houver token = sem sessao)
    val dados: Flow<DadosSessao?> = context.dataStore.data.map { p ->
        val token = p[TOKEN] ?: return@map null
        DadosSessao(
            token = token,
            utilizador = Utilizador(
                id = p[ID] ?: 0,
                nome = p[NOME].orEmpty(),
                email = p[EMAIL].orEmpty(),
                perfil = p[PERFIL].orEmpty(),
            ),
        )
    }

    // Guarda a sessao (depois do login ou da criacao do administrador)
    suspend fun guardar(sessao: DadosSessao) {
        context.dataStore.edit { p ->
            p[TOKEN] = sessao.token
            p[ID] = sessao.utilizador.id
            p[NOME] = sessao.utilizador.nome
            p[EMAIL] = sessao.utilizador.email.orEmpty()
            p[PERFIL] = sessao.utilizador.perfil
        }
    }

    // Apaga a sessao (logout)
    suspend fun limpar() {
        context.dataStore.edit { it.clear() }
    }

    // Chaves usadas no ficheiro de preferencias
    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val ID = longPreferencesKey("id")
        val NOME = stringPreferencesKey("nome")
        val EMAIL = stringPreferencesKey("email")
        val PERFIL = stringPreferencesKey("perfil")
    }
}
