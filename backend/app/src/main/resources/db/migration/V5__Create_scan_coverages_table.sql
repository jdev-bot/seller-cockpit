CREATE TABLE scan_coverages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_case_id UUID NOT NULL,
    grid_rows INT NOT NULL,
    grid_cols INT NOT NULL,
    covered_cells INT NOT NULL,
    total_cells INT NOT NULL,
    coverage_percent INT NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    is_complete BOOLEAN NOT NULL,
    auto_stopped BOOLEAN NOT NULL,
    cell_data JSONB,
    missing_regions JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_scan_coverage_product_case
        FOREIGN KEY (product_case_id)
        REFERENCES product_cases(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_scan_coverage_product_case
    ON scan_coverages(product_case_id);
