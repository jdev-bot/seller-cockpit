CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE product_cases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    seller_mode VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    title VARCHAR(500),
    
    -- Product facts embeddable
    product_facts_title VARCHAR(500),
    product_facts_brand VARCHAR(255),
    product_facts_model VARCHAR(255),
    product_facts_category VARCHAR(255),
    product_facts_variant VARCHAR(255),
    product_facts_color VARCHAR(255),
    product_facts_size_or_capacity VARCHAR(255),
    product_facts_accessories TEXT,
    product_facts_user_confirmed BOOLEAN,
    product_facts_confidence DOUBLE PRECISION,
    
    -- Condition assessment embeddable
    condition_assessment_condition VARCHAR(50),
    condition_assessment_visible_defects TEXT,
    condition_assessment_functionality_confirmed BOOLEAN,
    condition_assessment_missing_information TEXT,
    condition_assessment_confidence DOUBLE PRECISION,
    condition_assessment_user_confirmed BOOLEAN,
    
    -- Pricing profile embeddable
    pricing_profile_seller_mode VARCHAR(50),
    pricing_profile_purchase_price_amount DECIMAL(19,4),
    pricing_profile_purchase_price_currency VARCHAR(3) DEFAULT 'EUR',
    pricing_profile_purchase_price_includes_vat BOOLEAN,
    pricing_profile_shipping_cost_amount DECIMAL(19,4),
    pricing_profile_packaging_cost_amount DECIMAL(19,4),
    pricing_profile_other_costs_amount DECIMAL(19,4),
    pricing_profile_platform_fee_estimate_amount DECIMAL(19,4),
    pricing_profile_tax_mode VARCHAR(50),
    pricing_profile_vat_rate_percent DECIMAL(5,2),
    pricing_profile_target_margin_percent DECIMAL(5,2),
    pricing_profile_desired_minimum_price_amount DECIMAL(19,4),
    pricing_profile_desired_minimum_profit_amount DECIMAL(19,4),
    
    -- Pricing recommendation embeddable
    pricing_recommendation_quick_sale_price_amount DECIMAL(19,4),
    pricing_recommendation_recommended_price_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    pricing_recommendation_optimistic_price_amount DECIMAL(19,4),
    pricing_recommendation_break_even_price_amount DECIMAL(19,4),
    pricing_recommendation_do_not_sell_below_price_amount DECIMAL(19,4),
    pricing_recommendation_expected_payout_amount DECIMAL(19,4),
    pricing_recommendation_estimated_profit_amount DECIMAL(19,4),
    pricing_recommendation_roi_percent DECIMAL(10,4),
    pricing_recommendation_net_profit_amount DECIMAL(19,4),
    pricing_recommendation_margin_percent DECIMAL(10,4),
    pricing_recommendation_fee_breakdown_platform_fee_amount DECIMAL(19,4),
    pricing_recommendation_fee_breakdown_shipping_cost_amount DECIMAL(19,4),
    pricing_recommendation_fee_breakdown_packaging_cost_amount DECIMAL(19,4),
    pricing_recommendation_fee_breakdown_other_costs_amount DECIMAL(19,4),
    pricing_recommendation_fee_breakdown_total_costs_amount DECIMAL(19,4),
    pricing_recommendation_fee_breakdown_currency VARCHAR(3) DEFAULT 'EUR',
    pricing_recommendation_tax_breakdown_vat_rate_percent DECIMAL(5,2),
    pricing_recommendation_tax_breakdown_vat_amount DECIMAL(19,4),
    pricing_recommendation_tax_breakdown_net_revenue_amount DECIMAL(19,4),
    pricing_recommendation_tax_breakdown_gross_revenue_amount DECIMAL(19,4),
    pricing_recommendation_tax_breakdown_currency VARCHAR(3) DEFAULT 'EUR',
    pricing_recommendation_explanation TEXT,
    pricing_recommendation_confidence VARCHAR(20),
    pricing_recommendation_currency VARCHAR(3) DEFAULT 'EUR',
    
    -- Market research embeddable
    market_research_estimated_market_low_amount DECIMAL(19,4),
    market_research_estimated_market_mid_amount DECIMAL(19,4),
    market_research_estimated_market_high_amount DECIMAL(19,4),
    market_research_confidence VARCHAR(20),
    market_research_summary TEXT,
    market_research_warnings TEXT,
    market_research_currency VARCHAR(3) DEFAULT 'EUR',
    
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE product_case_missing_questions (
    product_case_id UUID NOT NULL REFERENCES product_cases(id) ON DELETE CASCADE,
    question TEXT NOT NULL
);

CREATE TABLE product_case_compliance_warnings (
    product_case_id UUID NOT NULL REFERENCES product_cases(id) ON DELETE CASCADE,
    warning TEXT NOT NULL
);

CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    product_case_id UUID NOT NULL REFERENCES product_cases(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL,
    storage_url TEXT NOT NULL,
    thumbnail_url TEXT,
    selected_for_listing BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE listing_drafts (
    id UUID PRIMARY KEY,
    product_case_id UUID NOT NULL REFERENCES product_cases(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(255),
    condition_text TEXT NOT NULL,
    price_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    image_ids JSONB,
    attributes JSONB,
    warnings JSONB,
    ready_to_publish BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE marketplace_listings (
    id UUID PRIMARY KEY,
    product_case_id UUID NOT NULL REFERENCES product_cases(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    external_listing_id VARCHAR(255),
    external_url TEXT,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    price_amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'EUR',
    published_at TIMESTAMP WITH TIME ZONE,
    last_synced_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE marketplace_connections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    account_id VARCHAR(255),
    account_name VARCHAR(255),
    access_token_encrypted TEXT,
    refresh_token_encrypted TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, platform)
);

CREATE INDEX idx_product_cases_user_id ON product_cases(user_id);
CREATE INDEX idx_product_cases_status ON product_cases(status);
CREATE INDEX idx_media_assets_product_case_id ON media_assets(product_case_id);
CREATE INDEX idx_listing_drafts_product_case_id ON listing_drafts(product_case_id);
CREATE INDEX idx_marketplace_listings_product_case_id ON marketplace_listings(product_case_id);
