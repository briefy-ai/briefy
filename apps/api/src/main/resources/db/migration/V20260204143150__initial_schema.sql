-- Initial schema for Briefy
-- Creates placeholder tables for all domain entities

CREATE TABLE sources (
    id UUID PRIMARY KEY
);

CREATE TABLE briefings (
    id UUID PRIMARY KEY
);

CREATE TABLE takeaways (
    id UUID PRIMARY KEY
);

CREATE TABLE topics (
    id UUID PRIMARY KEY
);

CREATE TABLE topic_links (
    id UUID PRIMARY KEY
);

CREATE TABLE enrichments (
    id UUID PRIMARY KEY
);

CREATE TABLE recalls (
    id UUID PRIMARY KEY
);
