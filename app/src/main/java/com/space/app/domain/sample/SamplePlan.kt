package com.space.app.domain.sample

import com.space.app.domain.model.NodeKind
import com.space.app.domain.model.Plan
import com.space.app.domain.model.PlanEdge
import com.space.app.domain.model.PlanNode

/**
 * Hardcoded sample plan used until the AI layer lands (M3).
 * Passes PlanValidator: 10 nodes, single start, acyclic.
 */
object SamplePlan {

    val plan: Plan = Plan(
        title = "Launch a coffee cart",
        nodes = listOf(
            PlanNode(
                id = "concept",
                title = "Define the concept",
                summary = "What you sell, to whom, and why it stands out.",
                detail = "Decide the core identity of the cart before spending anything: " +
                    "signature drinks, price band, and the one thing people will remember. " +
                    "Write it down in three sentences; every later decision traces back here.",
                kind = NodeKind.GOAL,
                subtasks = listOf(
                    "Pick 3 signature drinks",
                    "Define target customer",
                    "Write a one-line pitch"
                )
            ),
            PlanNode(
                id = "permits",
                title = "Research permits",
                summary = "Food handling, vending and location permits.",
                detail = "Requirements differ by city. Call the local health department and " +
                    "the markets you want to stand at. Budget 4-8 weeks for paperwork.",
                kind = NodeKind.STEP,
                subtasks = listOf(
                    "List required permits",
                    "Note costs and wait times",
                    "Start the longest one first"
                )
            ),
            PlanNode(
                id = "menu",
                title = "Set the menu",
                summary = "Short menu, fast to execute on a cart.",
                detail = "A cart menu should top out at six items. Fewer ingredients means " +
                    "less stock, less waste, faster service. Price each item for a 70% margin.",
                kind = NodeKind.STEP
            ),
            PlanNode(
                id = "budget",
                title = "Estimate the budget",
                summary = "Startup cost plus three months of runway.",
                detail = "Cart, equipment, permits, initial stock, insurance and a buffer. " +
                    "Whatever number you reach, add 20%.",
                kind = NodeKind.STEP
            ),
            PlanNode(
                id = "location",
                title = "Pick locations",
                summary = "Two or three regular spots that fit the concept.",
                detail = "Foot traffic, morning vs evening crowds, and whether the spot " +
                    "matches the pitch from the concept step. Visit at the times you plan to trade.",
                kind = NodeKind.DECISION
            ),
            PlanNode(
                id = "seasonality",
                title = "Seasonality risk",
                summary = "Rain and winter will cut trade sharply.",
                detail = " Outdoor carts lose a third of days to weather in most climates. " +
                    "Plan a covered spot or an indoor winter option before it happens.",
                kind = NodeKind.RISK
            ),
            PlanNode(
                id = "funding",
                title = "Secure funding",
                summary = "Savings, a small loan, or a partner.",
                detail = "Match the funding source to the budget estimate. Keep a hard rule: " +
                    "if the number doubles from the estimate, stop and re-plan.",
                kind = NodeKind.DECISION
            ),
            PlanNode(
                id = "cart",
                title = "Design the cart",
                summary = "Workflow first, looks second.",
                detail = "Layout the work surface so the busiest drink takes the fewest steps. " +
                    "Then make it look like the concept.",
                kind = NodeKind.STEP
            ),
            PlanNode(
                id = "launch",
                title = "Soft launch",
                summary = "Two quiet weekends, friends invited.",
                detail = "Serve the full menu, time every order, and note what ran out. " +
                    "Collect one piece of feedback per customer.",
                kind = NodeKind.STEP,
                subtasks = listOf(
                    "Pick two dates",
                    "Invite 20 people",
                    "Time every order"
                )
            ),
            PlanNode(
                id = "evaluate",
                title = "Evaluate and iterate",
                summary = "Cut what did not sell, double down on what did.",
                detail = "After the soft launch, compare against the concept pitch. " +
                    "Adjust the menu, the locations, or the prices - then repeat.",
                kind = NodeKind.GOAL
            )
        ),
        edges = listOf(
            PlanEdge(from = "concept", to = "permits"),
            PlanEdge(from = "concept", to = "menu"),
            PlanEdge(from = "concept", to = "budget"),
            PlanEdge(from = "permits", to = "location", label = "once approved"),
            PlanEdge(from = "location", to = "seasonality", label = "watch the weather"),
            PlanEdge(from = "budget", to = "funding", label = "if short"),
            PlanEdge(from = "menu", to = "cart"),
            PlanEdge(from = "location", to = "cart"),
            PlanEdge(from = "funding", to = "cart", label = "approved"),
            PlanEdge(from = "cart", to = "launch"),
            PlanEdge(from = "seasonality", to = "launch", label = "covered spot"),
            PlanEdge(from = "launch", to = "evaluate")
        )
    )
}
