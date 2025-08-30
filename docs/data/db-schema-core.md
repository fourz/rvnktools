# Lore Database Schema Core Documentation

This document outlines the core data structure for the Minecraft server lore system.

## Overview

The database is designed to store and manage all lore-related content for Minecraft servers. The schema supports various lore types including:

- Items (Legendary, Seasonal, Event-specific)
- Locations (Cities, Points of Interest)
- Characters
- Quests
- Historical Events

## Base Lore Entry

### 0. `lore_entry`

**Purpose:** Provides a unified reference point for all lore content types, enabling cross-type functionality and relationships.

**Key Relationships:**

- Referenced by all specific lore type tables (One-to-One)
- Referenced by `lore_submission` (One-to-Many): Submission history and versioning

**Key Fields:**

- `id` (Primary Key, VARCHAR(36)) - Unique identifier for the lore entry
- `entry_type` (VARCHAR(50) NOT NULL) - Type of lore entry (ITEM, LOCATION, CHARACTER, EVENT, QUEST, etc.)
- `name` (VARCHAR(100) NOT NULL) - Display name of the lore entry
- `description` (TEXT NOT NULL) - Brief description for search results and previews
- `created_at` (TIMESTAMP NOT NULL) - When the entry was created
- `submitted_by` (VARCHAR(36)) - Player who submitted the entry (nullable, foreign key to player.id)
- `is_approved` (BOOLEAN DEFAULT FALSE) - Whether the entry is approved
- `metadata` (TEXT) - JSON or text metadata for the entry

**Foreign Keys:**
- `submitted_by` references `player(id)`

---

## Supporting Tables

### `lore_submission`

**Purpose:** Tracks the submission, approval, viewing history, and versioning for lore entries.

**Key Relationships:**

- References `lore_entry.id` (Many-to-One): The entry being tracked
- Referenced by `lore_approval_workflow` (One-to-Many): Approval process steps
- Referenced by `lore_popularity_metric` (One-to-One): Popularity statistics
- Referenced by `lore_community_vote` (One-to-Many): Player votes
- Referenced by `lore_submission_tag` (Many-to-Many): Tags for this version

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier
- `entry_id` (INT NOT NULL) - Reference to the lore entry
- `slug` (VARCHAR(150) NOT NULL) - URL-friendly version of the name
- `visibility` (VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', CHECK (visibility IN ('PUBLIC', 'STAFF_ONLY', 'HIDDEN'))) - Who can see this submission
- `status` (VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL'))) - Current status
- `submitter_uuid` (CHAR(36) NOT NULL) - Who submitted the content
- `created_by` (CHAR(36) NOT NULL) - UUID of user who created this entry (may be different from submitter for staff edits)
- `submission_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When submitted
- `approval_status` (VARCHAR(20) NOT NULL DEFAULT 'PENDING') - Status in the approval process
- `approved_by` (CHAR(36)) - Staff who approved the entry
- `approved_at` (TIMESTAMP) - When the entry was approved
- `view_count` (INT NOT NULL DEFAULT 0) - Number of times viewed
- `last_viewed_at` (TIMESTAMP) - When the entry was last viewed
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)
- `content_version` (INT NOT NULL DEFAULT 1) - Version number for this submission
- `is_current_version` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether this is the active version
- `content` (JSON) - The actual content of this version
- CONSTRAINT `uq_lore_submission_entry_version` UNIQUE (`entry_id`, `content_version`) - Each version must be unique per entry
- CONSTRAINT `uq_lore_submission_slug` UNIQUE (`slug`) - Slugs must be unique across all submissions

### `lore_popularity_metric`

**Purpose:** Stores aggregated popularity statistics for lore submissions.

**Key Relationships:**

- References `lore_submission.id` (One-to-One): The submission being measured

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `submission_id` (INT NOT NULL) - Reference to the lore submission
- `total_votes` (INT NOT NULL DEFAULT 0) - Number of votes received
- `average_rating` (DECIMAL(3,2)) - Average vote value
- `trending_score` (DECIMAL(10,4)) - Calculated popularity score
- `favorite_count` (INT NOT NULL DEFAULT 0) - Number of times favorited
- `last_calculated_at` (TIMESTAMP) - When metrics were last updated
- `rank` (INT) - Relative rank within its category

### `lore_community_vote`

**Purpose:** Records player votes and feedback on lore submissions.

**Key Relationships:**

- References `lore_submission.id` (Many-to-One): The submission being voted on

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `submission_id` (INT NOT NULL) - Reference to the lore submission
- `player_uuid` (CHAR(36) NOT NULL) - Player who voted
- `vote_value` (INT NOT NULL) - Numeric vote value (1-5 stars, etc.)
- `vote_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the vote was cast
- `comment` (TEXT) - Optional feedback text
- `is_anonymous` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether the vote should be displayed anonymously

### `lore_tag`

**Purpose:** Stores categorization tags for lore content.

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(50) NOT NULL) - Tag name
- `category` (VARCHAR(50)) - Optional category grouping for this tag

### `lore_submission_tag`

**Purpose:** Junction table linking lore submissions to tags (many-to-many).

**Key Fields:**

- `submission_id` (INT NOT NULL) - Reference to the lore submission
- `tag_id` (INT NOT NULL) - Reference to the tag
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the tag was associated
- PRIMARY KEY (submission_id, tag_id)

### `lore_approval_workflow`

**Purpose:** Tracks the approval process for lore submissions.

**Key Relationships:**

- References `lore_submission.id` (Many-to-One): The submission being reviewed

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `submission_id` (INT NOT NULL) - Reference to the submission
- `stage` (VARCHAR(50) NOT NULL) - Current workflow stage (SUBMITTED, UNDER_REVIEW, etc.)
- `reviewer_uuid` (CHAR(36)) - Staff member reviewing this submission
- `stage_entered_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When the submission entered this stage
- `feedback` (TEXT) - Staff feedback for this stage
- `stage_completed_at` (TIMESTAMP) - When this stage was completed
- `next_stage` (VARCHAR(50)) - Next stage in the workflow

## Core Entities

### 1. `lore_item`

**Purpose:** Stores foundational information about all custom items in the Minecraft universe, representing both physical and virtual items of lore significance. This is a core entity that many other entities reference.

**Key Relationships:**

- References `lore_entry.id` through `lore_entry_id` (One-to-One): Base entry record
- Referenced by `collection_item` (Many-to-Many): Links items to collections
- Referenced by `lore_item_enchantment` (Many-to-Many): Associates enchantments with items
- References `lore_season.id` through `season_id` (Many-to-One): Associated season, if any

**Usage Scenarios:**

- Item Creation: When creating custom items for the server, all base properties are stored here
- Item Discovery: When players discover items, their discovery is recorded against this table
- Collection Management: Items are organized into collections for UI display and management
- Reward System: Items serve as rewards for various in-game activities

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the item, referenced throughout the system
- `lore_entry_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_entry(id)) - Link to base entry record
- `material` (VARCHAR(50) NOT NULL) - Base Minecraft material type (e.g., "player_head", "diamond_sword")
- `item_type` (VARCHAR(50) NOT NULL) - Categorizes items (LEGENDARY, SEASONAL, EVENT, COMMON, UNIQUE)
- `rarity` (VARCHAR(20) NOT NULL) - Rarity level affecting drop chances and display color (COMMON, UNCOMMON, RARE, EPIC, etc.)
- `is_obtainable` (BOOLEAN NOT NULL DEFAULT TRUE) - Whether players can currently obtain this item in-game
- `custom_model_data` (INT) - Integer for resource pack model mapping
- `season_id` (INT, FOREIGN KEY REFERENCES lore_season(id)) - Associated season, if any
- `is_vote_reward` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether this item can appear in voting rewards
- `drop_settings` JSON - Configuration for drop chances and quantities
- `item_properties` JSON NOT NULL - Flexible container for item-specific properties
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)
- `created_by` (CHAR(36)) - Who added this item

**Note:**

- The `lore_item` table does **not** store item descriptions. Descriptions for items are managed at the `lore_entry` and `lore_submission` level to support versioning and content history. Any logic or queries requiring item descriptions should reference those tables.

### 2. `lore_location`

**Purpose:** Records information about all significant locations in the server world, providing a geographical framework for the lore and player activities.

**Key Relationships:**

- References `lore_entry.id` through `lore_entry_id` (One-to-One): Base entry record
- Referenced by `lore_character.home_location_id` (One-to-Many): Characters reside at locations
- Referenced by `lore_event.location_id` (One-to-Many): Events occur at locations
- Referenced by `lore_quest.start_location_id` (One-to-Many): Quests begin at specific locations

**Usage Scenarios:**

- World Building: Creating richly detailed locations with histories and significance
- Quest Design: Establishing starting points and journey destinations for player quests
- Event Tracking: Recording where significant lore events have taken place
- Navigation Support: Helping players find significant locations in the world

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the location
- `lore_entry_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_entry(id)) - Link to base entry record
- `name` (VARCHAR(100) NOT NULL) - Official name of the location as known in the lore
- `description` (TEXT) - Detailed description of the location's appearance and significance
- `world_name` (VARCHAR(50) NOT NULL) - Literal Minecraft world name where location exists
- `location_type` (VARCHAR(50) NOT NULL) - Type of location (CITY, TOWN, LANDMARK, POI, DUNGEON)
- `coordinates` (VARCHAR(50) NOT NULL) - In-game coordinates
- `dimension` (VARCHAR(20) NOT NULL DEFAULT 'overworld') - Which dimension it exists in
- `founding_date` (DATE) - When the location was established
- `mayor` (VARCHAR(100)) - Current leader or authority figure
- `location_details` JSON - Additional flexible location properties

  ```json
  {
    "population": 150,
    "history": "Founded during the Great Winter..."
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 3. `lore_character`

**Purpose:** Stores information about all notable characters within the server lore, including NPCs, historical figures, and significant player characters.

**Key Relationships:**

- References `lore_entry.id` through `lore_entry_id` (One-to-One): Base entry record
- References `lore_location.id` through `home_location_id` (Many-to-One): Where characters reside
- Referenced by `lore_quest.quest_giver_id` (One-to-Many): Characters give quests to players
- Referenced by `lore_happening.related_characters` (Many-to-Many): Characters involved in happenings

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the character
- `lore_entry_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_entry(id)) - Link to base entry record
- `name` (VARCHAR(100) NOT NULL) - Character's primary name or identifier
- `title` (VARCHAR(100)) - Character's title or honorific that may change over time
- `description` (TEXT) - Physical description and key personality attributes
- `character_type` (VARCHAR(50) NOT NULL) - Type of character (NPC, HISTORICAL, MYTHICAL, PLAYER)
- `home_location_id` (INT, FOREIGN KEY REFERENCES lore_location(id)) - Where they reside
- `character_details` JSON - Flexible container for character-specific properties

  ```json
  {
    "occupation": "Blacksmith",
    "backstory": "Born in the northern reaches...",
    "relationships": [{"type": "sibling", "character_id": 12}],
    "cosmetic_appearance": {
      "skin_type": "DEFAULT",
      "base64_texture": "...",
      "model_type": "SLIM"
    }
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 4. `lore_happening`

**Purpose:** Records significant historical events, server activities, and roleplay moments that shape the server world and its history.

**Key Relationships:**

- References `lore_entry.id` through `lore_entry_id` (One-to-One): Base entry record
- References `lore_location.id` through `location_id` (Many-to-One): Where the happening occurred
- Referenced by `discord_conversation.related_happening_id` (One-to-Many): Discord discussions
- Referenced by `lore_character.notable_happenings` (Many-to-Many): Characters involved in happenings
- References `lore_roleplay_system.id` through `roleplay_system_id` (Many-to-One): System used for roleplay events

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the happening
- `lore_entry_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_entry(id)) - Link to base entry record
- `title` (VARCHAR(100) NOT NULL) - The title of this happening
- `description` (TEXT) - Detailed narrative of what occurred
- `happening_date` (TIMESTAMP NOT NULL) - When the happening occurred
- `happening_type` (VARCHAR(50) NOT NULL) - Type categorization (HISTORICAL, SEASONAL, PLAYER, SERVER, TRIAL, CELEBRATION, BUILD_EVENT, WAR, COMPETITION, ROLEPLAY, etc.)
- `location_id` (INT, FOREIGN KEY REFERENCES lore_location(id)) - Where it happened
- `roleplay_system_id` (INT, FOREIGN KEY REFERENCES lore_roleplay_system(id)) - For roleplay happenings
- `status` (VARCHAR(20) NOT NULL DEFAULT 'COMPLETED') - Current status (SCHEDULED, IN_PROGRESS, COMPLETED, ARCHIVED)
- `parent_happening_id` (INT, FOREIGN KEY REFERENCES lore_happening(id)) - For related happenings (e.g., a trial related to a war)
- `happening_details` JSON NOT NULL - Flexible container for happening-specific properties

  ```json
  {
    "participants": ["Player1", "Player2", "NPC_Lord_Frostmantle"],
    "significance": "Changed the political landscape of the northern region",
    "transcript": "transcripts/happening_20231215.md",
    "resolution": "Treaty of Whitewall was signed",
    "witnesses": ["Player3", "Player4"],
    "evidence": ["screenshot1.png", "screenshot2.png"],
    "organized_by": "ServerAdmin",
    "related_character_ids": [15, 22, 34],
    "related_location_ids": [42, 56],
    "screenshot_urls": ["img1.png", "img2.png"],
    "video_url": "https://youtube.com/...",
    "participant_count": 32,
    "authority_uuid": "38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e",
    "authority_name": "Judge DragonSlayer",
    "detailed_participants": [
      {"uuid": "123e4567-e89b-12d3-a456-426614174000", "name": "Player1", "role": "DEFENDANT"},
      {"uuid": "523e4567-e89b-12d3-a456-426614174111", "name": "Player2", "role": "PLAINTIFF"}
    ],
    "outcome": "New mayor elected",
    "consequences": "1 week in jail",
    "required_role_ids": ["JUDGE", "DEFENDANT", "PLAINTIFF"],
    "structure_phases": ["OPENING", "EVIDENCE", "TESTIMONY", "DELIBERATION", "VERDICT"],
    "legal_data": {
      "case_number": "CR-2023-01",
      "charges": ["Treason", "Theft"],
      "verdict": "Guilty"
    }
  }
  ```

- `has_transcript` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether a conversation transcript exists
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 5. `lore_season`

**Purpose:** Defines fixed seasonal periods in the game calendar, providing consistent date ranges for seasonal content.

**Key Relationships:**

- Referenced by `lore_item.season_id` (One-to-Many): Items associated with this season
- Referenced by `lore_voting_reward` (One-to-Many): Voting rewards for this season
- Referenced by `lore_voting_reward_schedule` (One-to-Many): Schedule for voting rewards during this season

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the season
- `name` (VARCHAR(50) NOT NULL) - Name of the season (WINTER, SPRING, SUMMER, FALL, etc.)
- `display_name` (VARCHAR(100) NOT NULL) - User-friendly name ("Winter Festival", "Summer Solstice", etc.)
- `start_date` (VARCHAR(10) NOT NULL) - Annual start date in MM-DD format
- `end_date` (VARCHAR(10) NOT NULL) - Annual end date in MM-DD format
- `season_type` (VARCHAR(50) NOT NULL) - Type of season (ANNUAL, HOLIDAY, EVENT, etc.)
- `theme_color` (VARCHAR(7)) - Hex color code for UI theming
- `icon_item_id` (INT) - Reference to a lore item used as icon
- `season_config` JSON - Flexible configuration for this season

  ```json
  {
    "has_custom_drops": true,
    "weather_effects": ["SNOW", "FREEZING"],
    "exclusive_shops": true,
    "announcement_message": "Winter has arrived in the realm!",
    "background_music": "winter_theme.ogg",
    "is_repeating": true,
    "chance_modifier": 1.5,
    "special_boss": "Frost Giant"
  }
  ```

- `is_active` (BOOLEAN NOT NULL DEFAULT TRUE) - Whether this season can be active
- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 6. `collection`

**Purpose:** Organizes items into thematic collections for player discovery and system organization.

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(100) NOT NULL) - Display name of the collection
- `description` (TEXT) - Description of what unites these items
- `collection_type` (VARCHAR(50) NOT NULL) - Type of collection (SEASONAL, MICKY_HATS, LEGENDARY, etc.)
- `collection_config` JSON - Configuration options for this collection

  ```json
  {
    "is_exclusive": false,
    "author": "ServerAdmin",
    "release_date": "2023-05-15",
    "icon_item_id": 42
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 7. `collection_item`

**Purpose:** Links items to collections (many-to-many relationship).

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `collection_id` (INT NOT NULL, FOREIGN KEY REFERENCES collection(id))
- `item_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_item(id))
- `sequence_number` (INT NOT NULL DEFAULT 0) - Display order within collection
- `item_config` JSON - Item-specific configuration within this collection

  ```json
  {
    "unlock_requirement": "Complete the Winter Quest",
    "rarity_tier": 3,
    "is_featured": true
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 8. `player_achievement`

**Purpose:** Records player interactions with lore elements.

**Key Relationships:**

- References `lore_submission.id` through `reference_id` (for achievements related to content versions)
- References `lore_entry.id` through `reference_id` (for achievements related to entity discovery)
- Enables tracking of player progression and interaction across the lore system

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `player_uuid` (CHAR(36) NOT NULL) - Player's unique identifier
- `player_name` (VARCHAR(36) NOT NULL) - Player's display name
- `achievement_type` (VARCHAR(50) NOT NULL) - Type of interaction (ITEM_FOUND, LOCATION_DISCOVERED, SUBMISSION_LIKED, etc.)
- `reference_id` (INT NOT NULL) - ID of the related lore element or submission
- `reference_type` (VARCHAR(20) NOT NULL) - Type of reference (ENTRY, SUBMISSION) to distinguish what the reference_id points to
- `achievement_date` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP) - When it happened
- `achievement_details` JSON - Additional achievement information

  ```json
  {
    "quantity": 1,
    "method": "CRAFTING",
    "server": "survival",
    "coordinates": "x:100,y:64,z:-200",
    "submission_version": 2
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 9. `lore_enchantment`

**Purpose:** Stores all enchantments in a single table, including both vanilla Minecraft and custom server enchantments.

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(100) NOT NULL) - System name of the enchantment (e.g., "frost_walker", "custom_fire_aspect")
- `display_name` (VARCHAR(100)) - In-game display name
- `description` (TEXT) - Description of what the enchantment does
- `lore_backstory` (TEXT) - The in-world story behind this enchantment
- `is_vanilla` (BOOLEAN NOT NULL DEFAULT FALSE) - Whether it's a standard Minecraft enchantment
- `max_level` (INT NOT NULL DEFAULT 1) - Maximum enchantment level
- `enchantment_properties` JSON - Flexible container for enchantment properties

  ```json
  {
    "minecraft_key": "minecraft:frost_walker",
    "is_treasure": true,
    "is_curse": false,
    "is_discoverable": true,
    "is_tradeable": true,
    "compatible_item_types": ["boots"],
    "conflicts": [3, 15],
    "effect_script": "scripts/enchants/frost_walker.js",
    "activation_trigger": "WALK_OVER_WATER",
    "discoverer_id": 42,
    "discovery_date": "2023-01-15",
    "visual_effects": {"particles": "SNOW", "sound": "block.glass.break"},
    "weight": 2,
    "rarity": "RARE"
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 10. `lore_item_enchantment`

**Purpose:** Links items with their applied enchantments, including custom level and effects.

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `item_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_item(id))
- `enchantment_id` (INT NOT NULL, FOREIGN KEY REFERENCES lore_enchantment(id))
- `level` (INT NOT NULL DEFAULT 1) - Level of the enchantment applied
- `enchantment_config` JSON - Item-specific enchantment configuration

  ```json
  {
    "is_hidden": false,
    "custom_effects": {"extra_damage": 2},
    "activation_trigger": "CRITICAL_HIT",
    "override_display_name": "Mega Frost II"
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 11. `special_entity`

**Purpose:** Tracks entities of special significance to the lore that are not characters or typical mobs.

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT)
- `name` (VARCHAR(100) NOT NULL)
- `entity_type` (VARCHAR(50) NOT NULL) - Type of entity (PHANTOM, VILLAGER, WARDEN, etc.)
- `description` (TEXT)
- `location_id` (INT, FOREIGN KEY REFERENCES lore_location(id))
- `entity_details` JSON - Flexible container for entity-specific properties

  ```json
  {
    "owner": "38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e",
    "creation_date": "2023-09-15",
    "is_deceased": true,
    "memorials": ["Memorial at Whitewall", "Shrine in the North"],
    "significance": "First phantom to be tamed",
    "legal_status": "Protected",
    "cosmetic_appearance": {
      "skin_type": "PHANTOM",
      "size": "LARGE",
      "texture_variant": "GLACIER"
    }
  }
  ```

- `created_at` (TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP)

### 12. `lore_roleplay_system`

**Purpose:** Defines structured roleplay frameworks available on the server, providing configuration for different types of roleplay activities that players can engage in.

**Key Relationships:**

- Referenced by `lore_happening` (One-to-Many): Individual roleplay happenings using this system
- References `lore_location.id` through `primary_location_id` (Many-to-One): Main location for this system
- May reference `lore_item.id` through `rule_book_id`: Item containing system rules

**Usage Scenarios:**

- System Configuration: Setting up parameters for various server roleplay systems
- Rule Enforcement: Defining processes for enforcing server rules through roleplay
- Community Interaction: Supporting player-driven storytelling and character development
- Structured Events: Providing frameworks for diverse roleplay activities

**Key Fields:**

- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the roleplay system
- `name` (VARCHAR(100) NOT NULL) - Name of the roleplay system
- `system_type` (VARCHAR(50) NOT NULL) - Type of system (LEGAL, GOVERNANCE, COMMERCE, GUILD, COMBAT, QUEST, EXPLORATION, SOCIAL, CRAFTING)
- `description` (TEXT) - Detailed description of the system's purpose and function
- `primary_location_id` (INT) - Main location for system activities
- `is_active` (BOOLEAN NOT NULL DEFAULT TRUE) - Whether the system is currently active
- `authority_role` (VARCHAR(100)) - Staff or player role that has authority within this system
- `participation_requirements` (TEXT) - What players need to participate (items, permissions, etc.)
- `system_config` JSON - Flexible container for system-specific configuration

  ```json
  {
    "rewards": {
      "enabled": true,
      "types": ["XP", "ITEMS", "CURRENCY", "REPUTATION"]
    },
    "mechanics": {
      "dice_rolling": true,
      "skill_checks": true,
      "combat_enabled": false
    },
    "progression": {
      "has_levels": true,
      "max_level": 10,
      "advancement_method": "PARTICIPATION"
    },
    "scheduling": {
      "recurring": true,
      "frequency": "WEEKLY",
      "duration_minutes": 60
    },
    "roles": ["LEADER", "PARTICIPANT", "OBSERVER"],
    "templates": [1, 3, 5]
  }
  ```

- `rule_book_id` (INT) - Item containing rules
- `last_updated` (TIMESTAMP) - When the system was last updated
- `documentation_url` (VARCHAR(255)) - Link to full documentation

## Supporting Module Integration

### Integration with Player Achievement System

The achievement system uses a flexible reference model to track player interactions:

```
┌─────────────────┐     ┌───────────────┐ 
│player_achievement├────►│  lore_entry   │ (for entity type reference)
└───────┬─────────┘     └───────────────┘ 
        │               ┌───────────────┐
        └───────────────►lore_submission│ (for content interaction and discovery)
                        └───────────────┘
```

When recording achievements, the system now primarily uses:

- `reference_type` = 'SUBMISSION'
- `reference_id` points to `lore_submission.id`

This approach allows a single achievement table to track interactions with all versioned content representations, with attributes like visibility and status being determined at the submission level.

For detailed relationship information, see [database-schema-data-relationships.md](./database-schema-data-relationships.md).

For taxonomies and classification systems, see [database-schema-data-taxonomy.md](./database-schema-data-taxonomy.md).

For constraint implementation details, see [db-schema-constraint-implementation.md](./db-schema-constraint-implementation.md).
