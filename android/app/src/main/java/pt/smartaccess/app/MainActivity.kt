// Ponto de entrada da app: a unica Activity. Todo o ecra e desenhado com Jetpack Compose.
package pt.smartaccess.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.smartaccess.app.ui.AppSmartAccess
import pt.smartaccess.app.ui.TemaSmartAccess

// Activity principal (declarada no AndroidManifest.xml como a que abre ao tocar no icone).
class MainActivity : ComponentActivity() {
    // Chamado pelo Android quando a app abre.
    // enableEdgeToEdge(): a app desenha tambem por baixo da barra de estado e da barra de navegacao.
    // setContent { ... }: em vez de layouts XML, mostra a interface Compose AppSmartAccess() com o tema da app.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaSmartAccess {
                AppSmartAccess()
            }
        }
    }
}
