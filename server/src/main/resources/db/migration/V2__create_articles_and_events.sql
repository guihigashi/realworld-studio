-- V2: Create articles table, indexes, and LISTEN/NOTIFY trigger for real-time queueing

CREATE TABLE IF NOT EXISTS articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES personas(id) ON DELETE CASCADE,
    topic TEXT NOT NULL,
    title TEXT,
    slug TEXT,
    description TEXT,
    body TEXT,
    tag_list TEXT[] NOT NULL DEFAULT '{}',
    status VARCHAR(50) NOT NULL,
    conduit_url TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_articles_status_created_at ON articles (status, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_articles_author_id ON articles (author_id);

CREATE OR REPLACE FUNCTION notify_article_event()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM pg_notify('article_events', json_build_object(
        'id', NEW.id,
        'author_id', NEW.author_id,
        'status', NEW.status,
        'updated_at', to_char(NEW.updated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
    )::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_article_status_changed ON articles;
CREATE TRIGGER trg_article_status_changed
AFTER INSERT OR UPDATE ON articles
FOR EACH ROW
EXECUTE FUNCTION notify_article_event();
