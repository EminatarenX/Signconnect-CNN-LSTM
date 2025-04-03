package com.example.signconnect.navigation


import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.signconnect.ui.camera.CameraScreen
import com.example.signconnect.ui.result.ResultScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Destinations {
    const val CAMERA_SCREEN = "camera"
    const val RESULT_SCREEN = "result/{videoUri}/{apiResponse}"

    fun resultScreenRoute(videoUri: String, apiResponse: String): String {
        val encodedVideoUri = URLEncoder.encode(videoUri, StandardCharsets.UTF_8.toString())
        val encodedApiResponse = URLEncoder.encode(apiResponse, StandardCharsets.UTF_8.toString())
        return "result/$encodedVideoUri/$encodedApiResponse"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.CAMERA_SCREEN
    ) {
        composable(Destinations.CAMERA_SCREEN) {
            CameraScreen(
                onNavigateToResult = { videoUri, apiResponse ->
                    navController.navigate(Destinations.resultScreenRoute(videoUri, apiResponse))
                }
            )
        }

        composable(
            route = Destinations.RESULT_SCREEN,
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType },
                navArgument("apiResponse") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoUri = URLDecoder.decode(
                backStackEntry.arguments?.getString("videoUri") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            val apiResponse = URLDecoder.decode(
                backStackEntry.arguments?.getString("apiResponse") ?: "",
                StandardCharsets.UTF_8.toString()
            )
            ResultScreen(
                videoUri = videoUri,
                apiResponse = apiResponse,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}