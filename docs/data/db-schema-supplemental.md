# RVNKLore Database Schema Supplemental

This document extends the core database schema with additional tables for enhanced functionality.

## Economy and Trading

### 12. `lore_item_value`

**Purpose:** Tracks the economic value and trading information for lore items, providing a framework for in-game economy and item valuation.

**Key Relationships:**
- References `lore_item.id` (Many-to-One): Items being valued
- Referenced by `lore_trading_record` (One-to-Many): Historical trade values used as reference
- May influence merchant NPC behavior and shop pricing

**Usage Scenarios:**
- Economic Balancing: Establishing fair values for different items in the economy
- Player Trading: Providing reference values for player-to-player trades
- Shop Systems: Setting baseline prices for automated shops
- Value Fluctuation: Tracking changes in item values over time for economic events

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for this valuation record
- `item_id` (INT NOT NULL) - The item being valued
- `base_value` (DECIMAL(10,2) NOT NULL) - Standard economic value in server currency
- `is_fluctuating` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether the value changes over time
- `last_known_trade_value` (DECIMAL(10,2)) - Most recent traded value
- `scarcity_factor` (DECIMAL(5,2)) - Multiplier based on rarity
- `value_timestamp` (TIMESTAMP) - When valuation was last updated
- `has_trading_restrictions` (BOOLEAN NOT NULL DEFAULT FALSE) - Any limitations on trading this item
- `currency_type` (VARCHAR(50) NOT NULL DEFAULT 'standard') - What currency this item is valued in
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 13. `lore_trading_record`

Records transactions involving lore items between players or with NPCs.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `item_id` (INT NOT NULL) - Item being traded
- `seller_uuid` (CHAR(36) NOT NULL) - UUID of the selling player or NPC identifier
- `buyer_uuid` (CHAR(36) NOT NULL) - UUID of the buying player or NPC identifier
- `trade_amount` (DECIMAL(10,2) NOT NULL) - Amount of currency exchanged
- `trade_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the trade occurred
- `location_id` (INT) - Where the trade took place
- `additional_items` (JSON) - Other items included in the trade
- `trade_notes` (TEXT) - Any special circumstances
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

## Content Management

### 14. `lore_submission`

**Purpose:** Manages player or staff submissions and versions of lore content.

**Key Relationships:**
- References `lore_entry.id` (Many-to-One): The entry being submitted/updated
- Referenced by `lore_approval_workflow` (One-to-Many): Approval process steps
- Referenced by `lore_popularity_metric` (One-to-One): Popularity statistics
- Referenced by `lore_community_vote` (One-to-Many): Player votes
- Referenced by `lore_submission_tag` (Many-to-Many): Tags for this version
- Referenced by `player_achievement` (One-to-Many): Player interactions with this submission

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `entry_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_entry(id)) - Which lore entry
- `submitter_uuid` (CHAR(36) NOT NULL) - Who submitted the content
- `submission_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When it was submitted
- `content` (JSON NOT NULL) - JSON structure containing the content being submitted (including item descriptions and other versioned fields)
- `approval_status` (VARCHAR(20) NOT NULL DEFAULT 'PENDING', CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REVISED'))) - Current status
- `approved_by` (CHAR(36)) - Staff who approved the submission
- `approved_at` (TIMESTAMP) - When this submission was approved
- `view_count` (INT NOT NULL DEFAULT 0) - Number of times viewed
- `last_viewed_at` (TIMESTAMP) - When the entry was last viewed
- `content_version` (INT NOT NULL DEFAULT 1) - Version number for this submission
- `is_current_version` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether this is the live version
- `notes` (TEXT) - Reviewer notes or feedback
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

**Note:**
- Item descriptions are versioned and stored in the `content` JSON of `lore_submission`. The `lore_item` table does **not** store descriptions. All description logic for items should reference the `lore_entry` or `lore_submission` tables.

### 15. `lore_approval_workflow`

Tracks the review and approval process for lore submissions.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `submission_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_submission(id)) - Reference to the submission
- `stage` (VARCHAR(50) NOT NULL, CHECK (stage IN ('SUBMITTED', 'UNDER_REVIEW', 'FEEDBACK', 'FINAL_REVIEW', 'APPROVED'))) - Current workflow stage
- `reviewer_uuid` (CHAR(36)) - Staff member reviewing this submission
- `stage_entered_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the submission entered this stage
- `feedback` (TEXT) - Staff feedback for this stage
- `stage_completed_at` (TIMESTAMP) - When this stage was completed
- `next_stage` (VARCHAR(50), CHECK (next_stage IN ('UNDER_REVIEW', 'FEEDBACK', 'FINAL_REVIEW', 'APPROVED', NULL))) - Next stage in the workflow
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

## Community Engagement

### 16. `lore_community_vote`

Records player votes and feedback on lore submissions.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `submission_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_submission(id)) - Which submission is being voted on
- `player_uuid` (CHAR(36) NOT NULL) - Player who voted
- `vote_value` (INT NOT NULL) - Numeric vote value (1-5 stars, etc.)
- `vote_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the vote was cast
- `comment` (TEXT) - Optional feedback text
- `is_anonymous` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether the vote should be displayed anonymously
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 17. `lore_popularity_metric`

Aggregates statistics on lore submission popularity.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `submission_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_submission(id)) - Which submission is being measured
- `total_votes` (INT NOT NULL DEFAULT 0) - Number of votes received
- `average_rating` (DECIMAL(3,2)) - Average vote value
- `trending_score` (DECIMAL(10,4)) - Calculated popularity score
- `favorite_count` (INT NOT NULL DEFAULT 0) - How many times favorited
- `last_calculated_at` (TIMESTAMP) - When metrics were last updated
- `rank` (INT) - Relative rank within its category
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 18. `lore_tag`

Stores categories and keywords for organizing lore content.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(50) NOT NULL) - Tag name
- `category` (VARCHAR(50)) - Optional grouping category
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)
- CONSTRAINT `uq_lore_tag_name` UNIQUE (`name`)

### 19. `lore_submission_tag`

Links tags to specific lore submissions.

**Key Fields:**
- `submission_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_submission(id))
- `tag_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_tag(id))
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- PRIMARY KEY (`submission_id`, `tag_id`)

## Player Interaction System

### 20. `player_achievement_type`

Defines the various types of achievements and interactions that can be tracked in the system.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(50) NOT NULL) - System name of the achievement type
- `display_name` (VARCHAR(100) NOT NULL) - User-friendly display name
- `description` (TEXT) - Description of what this achievement represents
- `reference_target` (VARCHAR(20) NOT NULL) - What this achievement type references (ENTRY, SUBMISSION)
- `category` (VARCHAR(50)) - Grouping category for related achievements
- `achievement_config` JSON - Achievement-specific configuration
  ```json
  {
    "points": 10,
    "minimum_quantity": 1,
    "maximum_quantity": null,
    "is_repeatable": false,
    "cooldown_minutes": 1440,
    "unlock_requirements": ["ITEM_FOUND:any:5"],
    "reward_commands": ["give %player% diamond 1", "eco give %player% 100"],
    "announcement_format": "%player% has discovered %item_name%!"
  }
  ```
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 21. `player_achievement`

Records individual instances of players achieving or interacting with lore content.

**Key Relationships:**
- References players through `player_uuid`
- References `player_achievement_type.id` through `achievement_type_id`
- References `lore_submission.id` or `lore_entry.id` through `reference_id` based on `reference_type`

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `player_uuid` (CHAR(36) NOT NULL) - Player's unique identifier
- `player_name` (VARCHAR(36) NOT NULL) - Player's display name
- `achievement_type_id` (INT NOT NULL) - Reference to the achievement type
- `reference_id` (INT NOT NULL) - ID of the referenced item (submission or entry)
- `reference_type` (VARCHAR(20) NOT NULL) - Type of reference (ENTRY, SUBMISSION)
- `quantity` (INT NOT NULL DEFAULT 1) - How many times or how much was achieved
- `achievement_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When it happened
- `server_id` (VARCHAR(20)) - Which server this happened on
- `details` JSON - Additional context-specific information
  ```json
  {
    "coordinates": "x:100,y:64,z:-200",
    "method": "CRAFTING",
    "previous_streak": 5,
    "new_streak": 6,
    "submission_version": 3,
    "context_data": {
      "related_quest_id": "winter_main_1",
      "npc_interaction": "blacksmith_tutorial"
    }
  }
  ```
- `is_announced` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether this has been announced
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)

### 22. `achievement_progress`

Tracks aggregate progress toward achievements that require multiple steps or quantities.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `player_uuid` (CHAR(36) NOT NULL) - Player's unique identifier
- `achievement_type_id` (INT NOT NULL) - Type of achievement being tracked
- `current_progress` (INT NOT NULL DEFAULT 0) - Current progress value
- `target_progress` (INT NOT NULL) - Required value to complete
- `first_progress_date` (TIMESTAMP NOT NULL) - When progress began
- `last_progress_date` (TIMESTAMP) - When progress was last updated
- `is_completed` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether target has been reached
- `completion_date` (TIMESTAMP) - When the achievement was completed
- `progress_details` JSON - Additional tracking information
  ```json
  {
    "milestones_reached": [10, 25, 50],
    "progress_history": [
      {"date": "2023-01-15T10:30:00", "value": 10},
      {"date": "2023-02-20T15:45:00", "value": 25}
    ],
    "related_reference_ids": [101, 102, 105, 108]
  }
  ```
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

## Achievement Relationships

The achievement system is designed to track player interaction with both entity discovery (lore_entry) and content engagement (lore_submission):

```
┌───────────────────┐     ┌───────────────────┐
│player_achievement_│     │ player_achievement │
│type               ├────►│                    │
└───────────────────┘     └──────┬─────┬──────┘
                                 │     │
                         ┌───────┘     └───────┐
                         ▼                     ▼
                 ┌───────────────┐     ┌───────────────┐
                 │  lore_entry   │     │lore_submission│
                 └───────────────┘     └───────────────┘
```

This structure enables:
- Tracking player discovery of entities (finding locations, meeting characters)
- Recording interaction with specific content versions (voting, commenting, viewing)
- Supporting both repeatable actions and one-time achievements
- Providing a foundation for achievement-based rewards and progression

For detailed constraint implementation, see [db-schema-constraint-implementation.md](./db-schema-constraint-implementation.md).

## Quest System

### 23. `lore_quest`

**Purpose:** Defines quests and adventures available to players, linking narrative objectives with gameplay rewards and progression.

**Key Relationships:**
- References `lore_entry.id` through `lore_entry_id` (One-to-One): Base entry record
- References `lore_character.id` through `quest_giver_id` (Many-to-One): Character who gives the quest
- References `lore_location.id` through `start_location_id` (Many-to-One): Where the quest begins
- Referenced by `player_achievement` (One-to-Many): Records when players complete quests
- May reference `lore_item` through JSON in `rewards` field: Items awarded upon completion

**Key Fields:**
- `id` (VARCHAR(50) PRIMARY KEY) - Unique identifier for the quest, often using semantic naming
- `lore_entry_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_entry(id)) - Link to base entry record
- `title` (VARCHAR(100) NOT NULL) - Display name shown to players in quest interfaces
- `description` (TEXT) - Detailed narrative description of the quest's purpose and background
- `quest_giver_id` (INT, FOREIGN KEY REFERENCES lore_character(id)) - Character who assigns the quest
- `start_location_id` (INT, FOREIGN KEY REFERENCES lore_location(id)) - Where to begin the quest
- `quest_type` (VARCHAR(50) NOT NULL) - Type of quest (MAIN, SIDE, EVENT, SEASONAL, DAILY)
- `difficulty` (INT) - Difficulty rating from 1-10
- `quest_config` JSON NOT NULL - Flexible container for quest configuration
  ```json
  {
    "prerequisites": ["main_quest_1", "side_quest_3"],
    "objectives": [
      {"type": "GATHER", "item": "diamond", "amount": 5},
      {"type": "VISIT", "location_id": 42}
    ],
    "rewards": [
      {"type": "ITEM", "item_id": 123, "amount": 1},
      {"type": "CURRENCY", "amount": 500},
      {"type": "EXPERIENCE", "amount": 1000}
    ]
  }
  ```
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 24. `lore_quest_objective`

**Purpose:** Tracks individual objectives within a quest, enabling complex multi-step adventures.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `quest_id` (VARCHAR(50) NOT NULL, FOREIGN KEY REFERENCES lore_quest(id))
- `objective_type` (VARCHAR(50) NOT NULL) - Type of objective (GATHER, KILL, VISIT, CRAFT, INTERACT)
- `sequence_number` (INT NOT NULL DEFAULT 0) - Order of objectives within quest
- `required_count` (INT NOT NULL DEFAULT 1) - How many are needed to complete
- `display_text` (VARCHAR(255) NOT NULL) - Text shown to players
- `objective_config` JSON NOT NULL - Configuration details
  ```json
  {
    "target_id": 42, // ID of target item, location, etc.
    "target_type": "ITEM", // What kind of target
    "completion_message": "You found all the required diamonds!",
    "is_hidden": false, // Whether players can see this objective
    "unlocks_objective_ids": [3, 4], // Which objectives become available after this
    "time_limit_seconds": 300 // Optional time limit
  }
  ```
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 25. `lore_quest_progress`

**Purpose:** Tracks player progress through quests.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `player_uuid` (CHAR(36) NOT NULL)
- `quest_id` (VARCHAR(50) NOT NULL, FOREIGN KEY REFERENCES lore_quest(id))
- `current_objective_id` (INT, FOREIGN KEY REFERENCES lore_quest_objective(id))
- `status` (VARCHAR(20) NOT NULL) - Current status (NOT_STARTED, IN_PROGRESS, COMPLETED, FAILED, ABANDONED)
- `start_date` (TIMESTAMP)
- `completion_date` (TIMESTAMP)
- `current_counts` JSON - Counts for each objective
  ```json
  {
    "1": 2, // Objective ID 1: collected 2 of 5 required items
    "2": 1  // Objective ID 2: visited 1 of 1 required locations
  }
  ```
- `last_updated` (TIMESTAMP)
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)

## Voting System

### 26. `lore_voting_reward`

**Purpose:** Manages the rewards given to players through the voting system.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `item_id` (INT, FOREIGN KEY REFERENCES lore_item(id)) - NULL for non-item rewards
- `season_id` (INT, FOREIGN KEY REFERENCES lore_season(id)) - Season when this reward is available
- `reward_name` (VARCHAR(100) NOT NULL) - Display name for the reward
- `reward_type` (VARCHAR(20) NOT NULL) - ITEM, CURRENCY, PERMISSION, COMMAND
- `reward_config` JSON NOT NULL - Configuration specific to this reward
  ```json
  {
    "chance": 0.05,
    "min_amount": 1, 
    "max_amount": 3,
    "commands": ["eco give %player% 100", "broadcast %player% received a reward!"],
    "message": "You received a reward!"
  }
  ```
- `is_active` (BOOLEAN NOT NULL DEFAULT TRUE)
- `reward_file` (VARCHAR(255)) - Path to reward file if applicable
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 27. `lore_vote_rotation`

**Purpose:** Controls the rotation schedules for seasonal items in voting rewards.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(100) NOT NULL) - Rotation name for identification
- `rotation_type` (VARCHAR(20) NOT NULL) - DAILY, WEEKLY, MONTHLY, SEASONAL, EVENT
- `schedule` JSON NOT NULL - Schedule configuration
  ```json
  {
    "start_date": "2023-12-01",
    "end_date": "2023-12-31",
    "refresh_interval_hours": 24,
    "item_count": 5,
    "items_per_vote": 2,
    "chance_modifier": 1.5,
    "exclusivity_group": "winter_rotation"
  }
  ```
- `is_active` (BOOLEAN NOT NULL DEFAULT TRUE)
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 28. `lore_voting_reward_history`

**Purpose:** Records the history of rewards given to players through voting.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `player_uuid` (CHAR(36) NOT NULL) - Player who received the reward
- `reward_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_voting_reward(id)) - Which reward was given
- `season_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_season(id)) - Which season was active
- `service_name` (VARCHAR(50) NOT NULL) - Voting service used
- `quantity` (INT NOT NULL DEFAULT 1) - How many of the reward were given
- `vote_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the vote occurred
- `vote_ip` (VARCHAR(45)) - IP address of the voter
- `vote_site_id` (VARCHAR(50)) - Site identifier
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)

### 29. `lore_voting_reward_schedule`

**Purpose:** Manages the scheduling of voting rewards.

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT)
- `season_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_season(id)) - Season this schedule applies to
- `schedule_name` (VARCHAR(100) NOT NULL) - Name for the schedule
- `start_date` (TIMESTAMP NOT NULL) - When this schedule begins
- `end_date` (TIMESTAMP NOT NULL) - When this schedule ends
- `is_active` (BOOLEAN NOT NULL DEFAULT TRUE) - Whether this schedule is active
- `priority` (INT NOT NULL DEFAULT 0) - Priority when schedules overlap
- `initiated_by` (CHAR(36)) - Who created this schedule
- `schedule_config` JSON - Configuration for this schedule
  ```json
  {
    "refresh_interval_hours": 24,
    "item_count": 5,
    "items_per_vote": 2,
    "chance_modifier": 1.5,
    "exclusivity_group": "winter_rotation"
  }
  ```
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

## Voting Relationships

The voting system has the following key relationships:

```
┌────────────────┐
│lore_season     ├───┐
└────────────────┘   │
                     │
                     ▼
┌────────────────┐  ┌────────────────┐  ┌────────────────┐
│lore_voting_    │  │lore_voting_    │  │lore_voting_    │
│reward          ├─►│reward_history  │◄─┤reward_schedule │
└────┬───────────┘  └────────────────┘  └────────────────┘
     │
     ▼
┌────────────────┐
│lore_item       │
└────────────────┘
```

This system allows for:
- Seasonal rotation of voting rewards integrated with the main season system
- Detailed tracking of voting patterns and rewards
- Scheduled changes to available rewards
- Flexible reward types (items, currency, commands)