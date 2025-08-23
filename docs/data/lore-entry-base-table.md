# Lore Entry Base Table

This document describes the `lore_entry` table which serves as a base entity for all lore-related content in the RVNKLore system. It provides a common structure for fundamental data across different lore types, with specialized data stored in related tables.

## Table Structure

### `lore_entry`

**Purpose:** Provides a unified reference point for all lore content types, enabling cross-type functionality with core shared attributes.

**Key Relationships:**
- Referenced by specific lore type tables (`lore_item`, `lore_location`, etc.) (One-to-One)
- Referenced by `lore_submission` (One-to-Many): Submission and version history for entries

**Usage Scenarios:**
- Content Identification: Assigning a unique identifier to any piece of lore content
- Cross-Type Functionality: Enabling functionality that works across all content types
- Relationships: Allowing any lore type to reference any other lore type
- Centralized Access: Providing a single point for finding any lore content

**Key Fields:**
- `id` (Primary Key, INT AUTO_INCREMENT) - Unique identifier for the lore entry
- `entry_type` (VARCHAR(50) NOT NULL) - Type of lore entry (ITEM, LOCATION, CHARACTER, EVENT, QUEST, etc.)
- `name` (VARCHAR(100) NOT NULL) - Display name of the lore entry
- CONSTRAINT `uq_lore_entry_name_type` UNIQUE (`name`, `entry_type`) - Allow same name for different types
- `description` (TEXT) - Brief description for search results and previews (not present in all lore types)

**Note:**
- Item descriptions are **not** stored in the `lore_item` table. For all item description needs, use the `lore_entry` or `lore_submission` tables, which support versioning and content history.

## Table Relationships Diagram

```
                      ┌────────────────┐
                      │   lore_entry   │
                      └───────┬────────┘
                              │
        ┌────────────────────┬┴───────────────────┐
        │                    │                    │
┌───────▼──────┐     ┌──────▼────────┐    ┌──────▼────────┐
│  lore_item   │     │ lore_location │    │lore_character │
└───────┬──────┘     └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
   lore_season         lore_happening      Other character
   collection_item           │                 tables
   lore_item_enchantment     │
                             │
                      lore_roleplay_system
```

## Version Control and Submissions

All content is versioned through the submission system, which now manages core attributes like slug, status, and visibility:

```
                     ┌─────────────┐
                     │ lore_entry  │
                     └──────┬──────┘
                            │
                    ┌───────▼───────┐
                    │lore_submission │◄───────┐
                    └─┬─────┬────┬──┘        │
                      │     │    │           │
         ┌────────────┘     │    └──────┐    │
         │                  │           │    │
┌────────▼────────┐  ┌─────▼────┐ ┌────▼────┴────┐
│lore_community_  │  │lore_      │ │lore_approval_│
│vote             │  │submission_│ │workflow      │
└─────────────────┘  │tag        │ └──────────────┘
                     └───┬───────┘
                         │
                    ┌────▼────┐
                    │lore_tag │
                    └─────────┘
```

## Related Tables

### `lore_submission`
Stores submission history, tracking data, and core attributes for lore entries:

```sql
CREATE TABLE lore_submission (
  id INT AUTO_INCREMENT PRIMARY KEY,
  entry_id INT NOT NULL,
  slug VARCHAR(150) NOT NULL,
  visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  submitter_uuid CHAR(36) NOT NULL,
  created_by CHAR(36) NOT NULL,
  submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  approved_by CHAR(36),
  approved_at TIMESTAMP,
  view_count INT NOT NULL DEFAULT 0,
  last_viewed_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP,
  content_version INT NOT NULL DEFAULT 1,
  submission_comments TEXT,
  content JSON,
  is_current_version BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_lore_submission_entry FOREIGN KEY (entry_id) REFERENCES lore_entry(id),
  CONSTRAINT uq_lore_submission_slug UNIQUE (slug),
  CONSTRAINT uq_lore_submission_entry_version UNIQUE (entry_id, content_version),
  CONSTRAINT ck_lore_submission_visibility CHECK (visibility IN ('PUBLIC', 'STAFF_ONLY', 'HIDDEN')),
  CONSTRAINT ck_lore_submission_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DRAFT', 'PENDING_APPROVAL'))
);
```

### `lore_popularity_metric`
Tracks popularity metrics for lore submissions:

```sql
CREATE TABLE lore_popularity_metric (
  id INT AUTO_INCREMENT PRIMARY KEY,
  submission_id INT NOT NULL,
  total_votes INT NOT NULL DEFAULT 0,
  average_rating DECIMAL(3,2),
  trending_score DECIMAL(10,4),
  favorite_count INT NOT NULL DEFAULT 0,
  last_calculated_at TIMESTAMP,
  rank INT,
  CONSTRAINT fk_lore_popularity_metric_submission FOREIGN KEY (submission_id) REFERENCES lore_submission(id),
  CONSTRAINT uq_lore_popularity_metric_submission UNIQUE (submission_id)
);
```

### `lore_community_vote`
Records player votes on lore submissions:

```sql
CREATE TABLE lore_community_vote (
  id INT AUTO_INCREMENT PRIMARY KEY,
  submission_id INT NOT NULL,
  player_uuid CHAR(36) NOT NULL,
  vote_value INT NOT NULL,
  vote_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  comment TEXT,
  is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_lore_community_vote_submission FOREIGN KEY (submission_id) REFERENCES lore_submission(id)
);
```

### `lore_tag` and `lore_submission_tag`
Provides a flexible tagging system for categorizing submissions:

```sql
CREATE TABLE lore_tag (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  category VARCHAR(50),
  CONSTRAINT uq_lore_tag_name UNIQUE (name)
);

CREATE TABLE lore_submission_tag (
  submission_id INT NOT NULL,
  tag_id INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (submission_id, tag_id),
  CONSTRAINT fk_lore_submission_tag_submission FOREIGN KEY (submission_id) REFERENCES lore_submission(id),
  CONSTRAINT fk_lore_submission_tag_tag FOREIGN KEY (tag_id) REFERENCES lore_tag(id)
);
```

### `lore_approval_workflow`
Tracks the review and approval process for submissions:

```sql
CREATE TABLE lore_approval_workflow (
  id INT AUTO_INCREMENT PRIMARY KEY,
  submission_id INT NOT NULL,
  stage VARCHAR(50) NOT NULL,
  reviewer_uuid CHAR(36),
  stage_entered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  feedback TEXT,
  stage_completed_at TIMESTAMP,
  next_stage VARCHAR(50),
  CONSTRAINT fk_lore_approval_workflow_submission FOREIGN KEY (submission_id) REFERENCES lore_submission(id)
);
```

### `lore_season`
Defines fixed seasonal periods in the game calendar, providing consistent date ranges for seasonal content:

```sql
CREATE TABLE lore_season (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  start_date VARCHAR(10) NOT NULL, -- MM-DD format
  end_date VARCHAR(10) NOT NULL, -- MM-DD format
  season_type VARCHAR(50) NOT NULL,
  theme_color VARCHAR(7),
  icon_item_id INT,
  season_config JSON,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP,
  
  CONSTRAINT uq_lore_season_name UNIQUE (name)
);
```

### `lore_happening`
Records significant historical events, server activities, and roleplay moments that shape the server world and its history:

```sql
CREATE TABLE lore_happening (
  id INT AUTO_INCREMENT PRIMARY KEY,
  lore_entry_id INT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT,
  happening_date TIMESTAMP NOT NULL,
  happening_type VARCHAR(50) NOT NULL,
  location_id INT,
  roleplay_system_id INT,
  parent_happening_id INT,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  happening_details JSON NOT NULL,
  has_transcript BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP,
  
  CONSTRAINT fk_lore_happening_entry FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id),
  CONSTRAINT fk_lore_happening_location FOREIGN KEY (location_id) REFERENCES lore_location(id),
  CONSTRAINT fk_lore_happening_system FOREIGN KEY (roleplay_system_id) REFERENCES lore_roleplay_system(id),
  CONSTRAINT fk_lore_happening_parent FOREIGN KEY (parent_happening_id) REFERENCES lore_happening(id),
  CONSTRAINT uq_lore_happening_entry UNIQUE (lore_entry_id)
);
```

## Player Achievement System

The system tracks player interactions with both lore entries (discovery) and submissions (engagement) using the `reference_type` field:

```sql
CREATE TABLE player_achievement (
  id INT AUTO_INCREMENT PRIMARY KEY,
  player_uuid CHAR(36) NOT NULL,
  player_name VARCHAR(36) NOT NULL,
  achievement_type_id INT NOT NULL,
  reference_id INT NOT NULL,
  reference_type VARCHAR(20) NOT NULL, -- 'ENTRY' or 'SUBMISSION'
  quantity INT NOT NULL DEFAULT 1,
  achievement_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  details JSON,
  CONSTRAINT ck_player_achievement_reference_type CHECK (reference_type IN ('ENTRY', 'SUBMISSION'))
);
```

## Implementation for Lore Types

All lore type tables include a reference to the base `lore_entry` table. **Descriptions for items are not stored in `lore_item`, but in the related `lore_entry` and versioned in `lore_submission`.**

```sql
ALTER TABLE lore_item 
ADD COLUMN lore_entry_id INT NOT NULL AFTER id,
ADD COLUMN season_id INT,
ADD COLUMN is_vote_reward_eligible BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN drop_settings JSON,
ADD CONSTRAINT fk_lore_item_entry FOREIGN KEY (lore_entry_id) REFERENCES lore_entry(id) ON DELETE CASCADE,
ADD CONSTRAINT fk_lore_item_season FOREIGN KEY (season_id) REFERENCES lore_season(id) ON DELETE SET NULL,
ADD CONSTRAINT uq_lore_item_entry UNIQUE (lore_entry_id);

-- Similar structure for other lore types
```

## Content Versioning

Each `lore_entry` can have multiple `lore_submission` records, but only one is marked as the current version:

```
Entry ID: 201 - "Frost Sword of the North"
  └─ Submission ID: 301 - Version 1 (created 2023-01-01, is_current_version=FALSE)
  └─ Submission ID: 302 - Version 2 (created 2023-02-15, is_current_version=FALSE)
  └─ Submission ID: 303 - Version 3 (created 2023-06-20, is_current_version=TRUE)
```

## Creation Process Flow

### Creating New Lore Content
When creating any new lore content, the application should:

1. Begin a transaction
2. Create a `lore_entry` record with common attributes:
   ```sql
   INSERT INTO lore_entry (entry_type, name)
   VALUES ('ITEM', 'Frost Sword');
   ```
3. Get the generated ID:
   ```sql
   SET @entry_id = LAST_INSERT_ID();
   ```
4. Create the specialized record using the entry ID:
   ```sql
   INSERT INTO lore_item (lore_entry_id, material, item_type, rarity, is_obtainable, item_properties)
   VALUES (@entry_id, 'DIAMOND_SWORD', 'LEGENDARY', 'EPIC', TRUE, '{"custom_model_data": 12345}');
   ```
5. Create submission record with content attributes:
   ```sql
   INSERT INTO lore_submission (
     entry_id, 
     slug,
     visibility,
     status,
     submitter_uuid, 
     created_by,
     content, 
     is_current_version
   )
   VALUES (
     @entry_id, 
     'frost-sword',
     'PUBLIC',
     'ACTIVE',
     '38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e',
     '38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e',
     '{"material": "DIAMOND_SWORD", "custom_model_data": 12345, "description": "A sword infused with ice magic"}', 
     TRUE
   );
   
   SET @submission_id = LAST_INSERT_ID();
   ```
6. Create associated records:
   ```sql
   -- Add popularity metric record
   INSERT INTO lore_popularity_metric (submission_id)
   VALUES (@submission_id);
   
   -- Add tags
   INSERT INTO lore_submission_tag (submission_id, tag_id)
   VALUES (@submission_id, 5), (@submission_id, 12), (@submission_id, 18);
   ```
7. Commit the transaction

### Creating New Versions of Content
When updating existing content:

1. Begin a transaction
2. Create a new submission record:
   ```sql
   INSERT INTO lore_submission (
      entry_id,
      slug,
      visibility,
      status,
      submitter_uuid,
      created_by,
      content, 
      content_version, 
      is_current_version
   )
   SELECT 
      @entry_id,
      s.slug, -- typically keep the same slug for continuity
      s.visibility, -- maintain visibility setting
      s.status, -- maintain status
      '38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e', -- submitter
      '38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e', -- creator
      '{"material": "DIAMOND_SWORD", "custom_model_data": 12346}', -- updated content
      (SELECT MAX(content_version) + 1 FROM lore_submission WHERE entry_id = @entry_id),
      TRUE -- this will automatically set other versions to FALSE through triggers
   FROM lore_submission s
   WHERE s.entry_id = @entry_id AND s.is_current_version = TRUE;
   
   SET @new_submission_id = LAST_INSERT_ID();
   ```
3. Create associated records for the new version:
   ```sql
   -- Copy existing tags and add new ones
   INSERT INTO lore_submission_tag (submission_id, tag_id)
   SELECT @new_submission_id, tag_id
   FROM lore_submission_tag
   WHERE submission_id = @old_submission_id;
   
   -- Add new popularity metric record
   INSERT INTO lore_popularity_metric (submission_id)
   VALUES (@new_submission_id);
   ```
4. Commit the transaction

## Query Examples

### Finding Content Across Types
```sql
-- Get most viewed content of any type
SELECT e.id, e.name, e.entry_type, s.view_count
FROM lore_entry e
JOIN lore_submission s ON e.id = s.entry_id
WHERE s.status = 'ACTIVE' 
  AND s.visibility = 'PUBLIC' 
  AND s.is_current_version = TRUE
ORDER BY s.view_count DESC
LIMIT 10;

-- Search across all lore types with tags
SELECT DISTINCT e.id, e.name, e.entry_type, s.content->>'$.description' AS description
FROM lore_entry e
JOIN lore_submission s ON e.id = s.entry_id
JOIN lore_submission_tag st ON s.id = st.submission_id
JOIN lore_tag t ON st.tag_id = t.id
WHERE t.name = 'winter' 
  AND s.is_current_version = TRUE
  AND s.visibility = 'PUBLIC'
ORDER BY e.name;
```

### Viewing Content Version History
```sql
-- View all versions of a specific lore entry
SELECT 
    s.id AS submission_id,
    s.content_version,
    s.submission_date,
    s.submitter_uuid,
    s.approval_status,
    s.is_current_version,
    s.view_count,
    COALESCE(u.username, 'Unknown') AS submitted_by,
    pm.total_votes,
    pm.average_rating
FROM lore_submission s
LEFT JOIN users u ON s.submitter_uuid = u.uuid
LEFT JOIN lore_popularity_metric pm ON s.id = pm.submission_id
WHERE s.entry_id = 201
ORDER BY s.content_version DESC;
```

### Finding Popular Content
```sql
-- Find the highest rated submissions
SELECT 
    e.id AS entry_id,
    e.name,
    e.entry_type,
    pm.average_rating,
    pm.total_votes,
    pm.favorite_count,
    s.view_count,
    s.content_version
FROM lore_entry e
JOIN lore_submission s ON e.id = s.entry_id AND s.is_current_version = TRUE
JOIN lore_popularity_metric pm ON s.id = pm.submission_id
WHERE e.visibility = 'PUBLIC'
ORDER BY pm.average_rating DESC, pm.total_votes DESC
LIMIT 20;
```

### Getting Content with Tags
```sql
-- Find content with specific tags
SELECT 
    e.id AS entry_id,
    e.name,
    e.entry_type,
    s.content->>'$.description' AS description,
    GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ', ') AS tags
FROM lore_entry e
JOIN lore_submission s ON e.id = s.entry_id AND s.is_current_version = TRUE
JOIN lore_submission_tag st ON s.id = st.submission_id
JOIN lore_tag t ON st.tag_id = t.id
WHERE s.visibility = 'PUBLIC'
GROUP BY e.id, e.name, e.entry_type, s.content->>'$.description'
HAVING 
    GROUP_CONCAT(t.name ORDER BY t.name) LIKE '%winter%'
    AND GROUP_CONCAT(t.name ORDER BY t.name) LIKE '%legendary%'
ORDER BY e.name;
```

### Player Achievement Queries
```sql
-- Find achievements for a specific player
SELECT 
    pa.id,
    pat.name AS achievement_type,
    e.name AS entry_name,
    e.entry_type,
    s.content_version,
    pa.quantity,
    pa.achievement_date
FROM player_achievement pa
JOIN player_achievement_type pat ON pa.achievement_type_id = pat.id
JOIN lore_submission s ON pa.reference_id = s.id
JOIN lore_entry e ON s.entry_id = e.id
WHERE pa.player_uuid = '38a5f8cf-e5a0-42a8-9f51-55c8a88a8e0e'
ORDER BY pa.achievement_date DESC;
```

### Seasonal Item Queries
```sql
-- Find all items for a specific season
SELECT e.id, e.name, li.rarity, s.display_name AS season
FROM lore_entry e
JOIN lore_item li ON e.id = li.lore_entry_id
JOIN lore_season s ON li.season_id = s.id
WHERE s.name = 'WINTER'
  AND e.visibility = 'PUBLIC'
ORDER BY li.rarity DESC, e.name;

-- Find voting reward eligible items for the current season
SELECT 
    e.id, 
    e.name, 
    li.rarity, 
    vr.reward_name,
    s.display_name AS season
FROM lore_entry e
JOIN lore_item li ON e.id = li.lore_entry_id
JOIN lore_season s ON li.season_id = s.id
LEFT JOIN lore_voting_reward vr ON li.id = vr.item_id AND s.id = vr.season_id
WHERE li.is_vote_reward_eligible = TRUE
  AND s.is_active = TRUE
  AND e.visibility = 'PUBLIC'
ORDER BY li.rarity DESC, e.name;

-- Find if a date is within a season's range
SELECT s.name, s.display_name
FROM lore_season s
WHERE ('12-25' BETWEEN s.start_date AND s.end_date
       OR (s.start_date > s.end_date AND ('12-25' >= s.start_date OR '12-25' <= s.end_date)))
  AND s.is_active = TRUE;
```

### Voting Reward Queries
```sql
-- Find active voting rewards for a specific season
SELECT 
    vr.reward_name,
    e.name AS item_name,
    vr.reward_type,
    s.display_name AS season
FROM lore_voting_reward vr
LEFT JOIN lore_item li ON vr.item_id = li.id
LEFT JOIN lore_entry e ON li.lore_entry_id = e.id
JOIN lore_season s ON vr.season_id = s.id
WHERE s.name = 'WINTER'
  AND vr.is_active = TRUE
ORDER BY vr.reward_type;

-- Get schedule for active voting rewards
SELECT 
    vrs.schedule_name,
    s.display_name AS season,
    vrs.start_date,
    vrs.end_date,
    COUNT(vr.id) AS reward_count
FROM lore_voting_reward_schedule vrs
JOIN lore_season s ON vrs.season_id = s.id
JOIN lore_voting_reward vr ON vrs.season_id = vr.season_id
WHERE vrs.is_active = TRUE
  AND vr.is_active = TRUE
GROUP BY vrs.id, vrs.schedule_name, s.display_name, vrs.start_date, vrs.end_date
ORDER BY vrs.start_date;
```

### Happenings Queries
```sql
-- Find upcoming happenings
SELECT 
    e.id, 
    e.name, 
    h.happening_date, 
    h.happening_type,
    l.name AS location_name, 
    p.name AS parent_happening
FROM lore_entry e
JOIN lore_happening h ON e.id = h.lore_entry_id
LEFT JOIN lore_location l ON h.location_id = l.id
LEFT JOIN lore_happening ph ON h.parent_happening_id = ph.id
LEFT JOIN lore_entry p ON ph.lore_entry_id = p.id
WHERE h.happening_date > CURRENT_TIMESTAMP
  AND h.status = 'SCHEDULED'
ORDER BY h.happening_date;

-- Find happenings for a specific location
SELECT 
    e.id, 
    e.name, 
    h.happening_date, 
    h.happening_type,
    h.status
FROM lore_entry e
JOIN lore_happening h ON e.id = h.lore_entry_id
WHERE h.location_id = 42
ORDER BY h.happening_date DESC;

-- Find all happenings of a specific type
SELECT 
    e.id, 
    e.name, 
    h.happening_date,
    l.name AS location_name
FROM lore_entry e
JOIN lore_happening h ON e.id = h.lore_entry_id
LEFT JOIN lore_location l ON h.location_id = l.id
WHERE h.happening_type = 'WAR'
ORDER BY h.happening_date DESC;

-- Find related happenings (e.g., all events related to a major happening)
SELECT 
    e.id, 
    e.name, 
    h.happening_type,
    h.happening_date
FROM lore_entry e
JOIN lore_happening h ON e.id = h.lore_entry_id
WHERE h.parent_happening_id = 103
ORDER BY h.happening_date;
```

## Benefits of the Updated Structure

- **Clean Separation of Concerns:** Each table focuses on a specific aspect of the data
- **Content Versioning:** Full history of all content changes is preserved
- **Engagement Tracking:** Votes, popularity metrics, and views tied to specific content versions
- **Flexible Tagging:** Tags applied to specific versions of content
- **Improved Analytics:** Detailed tracking of how content evolves over time
- **Structured Workflow:** Comprehensive approval pipeline for all content changes
- **Achievement Integration:** Unified achievement system that works with both entries and submissions
- **Seasonal Management:** Clear definition of seasons with consistent date ranges
- **Event Handling:** Comprehensive system for tracking server happenings
