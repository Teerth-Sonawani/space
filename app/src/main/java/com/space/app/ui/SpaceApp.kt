package com.space.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.space.app.domain.sample.SamplePlan
import com.space.app.ui.screens.chart.ChartScreen
import com.space.app.ui.screens.generating.GeneratingScreen
import com.space.app.ui.screens.home.HomeScreen
import com.space.app.ui.screens.idea.IdeaScreen
import com.space.app.ui.screens.library.LibraryScreen
import com.space.app.ui.screens.questions.QuestionsScreen
import com.space.app.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val IDEA = "idea"
    const val QUESTIONS = "questions"
    const val GENERATING = "generating"
    const val CHART = "chart"
    const val SAMPLE = "sample"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
}

@Composable
fun SpaceApp(viewModel: PlanFlowViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideInHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                initialOffsetX = { it / 5 }
            ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        },
        exitTransition = {
            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        },
        popEnterTransition = {
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                targetOffsetX = { it / 5 }
            ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                recentPlanTitle = viewModel.recentPlan?.plan?.title,
                savedPlanCount = viewModel.savedPlans.size,
                onNewIdea = {
                    viewModel.startNewIdea()
                    navController.navigate(Routes.IDEA) { launchSingleTop = true }
                },
                onOpenRecent = {
                    viewModel.openRecentPlan { recent ->
                        if (recent != null) {
                            navController.navigate(Routes.CHART) { launchSingleTop = true }
                        }
                    }
                },
                onOpenSample = {
                    navController.navigate(Routes.SAMPLE) { launchSingleTop = true }
                },
                onOpenLibrary = {
                    navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
                },
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.IDEA) {
            IdeaScreen(
                idea = viewModel.idea,
                onIdeaChange = viewModel::updateIdea,
                onBack = { navController.popBackStack() },
                onContinue = {
                    viewModel.saveDraftNow()
                    viewModel.loadQuestions()
                    navController.navigate(Routes.QUESTIONS) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.QUESTIONS) {
            LaunchedEffect(Unit) { viewModel.loadQuestionsIfNeeded() }
            QuestionsScreen(
                questions = viewModel.clarifyingQuestions.toList(),
                loadState = viewModel.questionsLoad,
                fromOfflinePack =
                    (viewModel.questionsLoad as? QuestionsLoadState.Ready)?.fromOfflinePack == true,
                onBack = { navController.popBackStack() },
                onAnswer = viewModel::recordAnswer,
                onDone = {
                    navController.navigate(Routes.GENERATING) {
                        popUpTo(Routes.HOME)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.GENERATING) {
            GeneratingScreen(
                state = viewModel.generation,
                onStart = viewModel::generate,
                onCancel = {
                    viewModel.cancelGeneration()
                    navController.popBackStack()
                },
                onReady = {
                    navController.navigate(Routes.CHART) { launchSingleTop = true }
                },
                onRetry = {
                    viewModel.retryGeneration()
                    viewModel.generate()
                },
                onUseStarter = viewModel::useStarterPlan
            )
        }
        composable(Routes.CHART) {
            ChartScreen(
                plan = viewModel.plan ?: SamplePlan.plan,
                onBack = { navController.popBackStack(Routes.HOME, false) }
            )
        }
        composable(Routes.SAMPLE) {
            ChartScreen(
                plan = SamplePlan.plan,
                onBack = { navController.popBackStack(Routes.HOME, false) }
            )
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                plans = viewModel.savedPlans,
                onBack = { navController.popBackStack(Routes.HOME, false) },
                onOpen = { stored ->
                    viewModel.openPlan(stored)
                    navController.navigate(Routes.CHART) { launchSingleTop = true }
                },
                onRename = viewModel::renamePlan,
                onDelete = viewModel::deletePlan,
                onUndoDelete = viewModel::undoDelete
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                sources = viewModel.sources,
                health = viewModel.sourceHealth,
                onBack = { navController.popBackStack() },
                onAdd = { provider, label, key ->
                    viewModel.addApiSource(provider, label, key)
                },
                onRemove = viewModel::removeApiSource,
                onMoveUp = { id -> viewModel.moveApiSource(id, delta = -1) },
                onMoveDown = { id -> viewModel.moveApiSource(id, delta = 1) }
            )
        }
    }
}
