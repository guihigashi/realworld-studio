-- V1: Create personas, tags, and persona_tags tables

CREATE TABLE IF NOT EXISTS tags (
    id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL CHECK (name = LOWER(name))
);

CREATE TABLE IF NOT EXISTS personas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    bio TEXT NOT NULL,
    avatar_url TEXT,
    writing_tone VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS persona_tags (
    persona_id UUID NOT NULL REFERENCES personas(id) ON DELETE CASCADE,
    tag_id INT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (persona_id, tag_id)
);

-- Seed predefined domain expertise tags (all lowercase)
INSERT INTO tags (name) VALUES
    ('scala'),
    ('zio'),
    ('functional-programming'),
    ('distributed-systems'),
    ('rust'),
    ('cloud-native'),
    ('event-driven'),
    ('database-internals'),
    ('devops'),
    ('generative-ai'),
    ('machine-learning'),
    ('type-safety'),
    ('reactive-systems')
ON CONFLICT (name) DO NOTHING;
