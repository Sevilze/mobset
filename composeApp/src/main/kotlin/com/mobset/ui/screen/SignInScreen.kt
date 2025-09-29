package com.mobset.ui.screen

import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.mobset.R
import com.mobset.ui.viewmodel.AuthUiState
import com.mobset.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
    onSignedIn: () -> Unit = {}
) {
    val uiState by viewModel.authUiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SignInContent(
        uiState = uiState,
        onContinueWithGoogle = {
            scope.launch {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        .build()
                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()
                    val resp: GetCredentialResponse = CredentialManager.create(context)
                        .getCredential(context, request)
                    val cred = GoogleIdTokenCredential.createFrom(resp.credential.data)
                    viewModel.signInWithGoogleIdToken(cred.idToken)
                    onSignedIn()
                } catch (e: GetCredentialException) {
                    // user cancelled or no credential
                } catch (e: Exception) { /* unexpected */
                }
            }
        },
        modifier = modifier
    )
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SignInContent(
    uiState: AuthUiState,
    onContinueWithGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Sign in to sync your profile and progress",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        Button(onClick = onContinueWithGoogle) {
            Text("Continue with Google")
        }

        Spacer(Modifier.height(16.dp))

        when (uiState) {
            is AuthUiState.Loading -> {
                androidx.compose.material3.ContainedLoadingIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Signing in…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is AuthUiState.Error -> {
                val msg = (uiState as AuthUiState.Error).message
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            else -> Unit
        }
    }
}

@Preview
@Composable
private fun SignInContentPreview() {
    SignInContent(uiState = AuthUiState.Idle, onContinueWithGoogle = {})
}
