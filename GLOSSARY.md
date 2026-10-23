# Billing App POS

Single-context store point-of-sale (POS) and inventory billing system for retail and food & beverage operations.

## Language

### Core Commerce

**Order**:
A customer bill and line-item transaction created during a checkout session at the point of sale.
_Avoid_: Purchase, ticket, invoice

**Customer**:
A buyer whose phone number and profile are tracked for CRM metrics, purchase history, and promotion eligibility.
_Avoid_: Client, shopper, account

**User**:
A staff employee or administrator authenticated with an email and role to operate the POS terminal or administrative screens.
_Avoid_: Account, member, cashier (when referring to the system user identity)

### Catalog & Inventory

**Item**:
A top-level catalog entry representing a product category offering.
_Avoid_: Product, good, merchandise

**Variant**:
A physical stock-keeping unit (SKU) of an Item defined by distinct attributes (size, color) with tracked ledger stock quantity.
_Avoid_: Sub-item, child product

**Modifier**:
A customizable add-on option belonging to a Modifier Group (e.g. Sugar level, Ice level, Extra topping) that adjusts price or preparation instructions without individual stock tracking.
_Avoid_: Topping, option, extra

**InventoryTransaction**:
An immutable ledger record tracking any stock movement (IN, OUT, ADJUSTMENT) for a Variant SKU.
_Avoid_: Stock change, stock log

### Promotions & Audit

**Promotion**:
A configured discount scheme (COUPON, HAPPY_HOUR, or BOGO) evaluated by the server to compute line discounts without stacking.
_Avoid_: Deal, voucher, sale

**Coupon Code**:
A cashier- or customer-entered alphanumeric code mapped to a single COUPON promotion.
_Avoid_: Promo code, discount token

**ActivityLog**:
An immutable audit trail entry recording the actor (user email), action (CREATE, UPDATE, DELETE), entity type, entity ID, and timestamp.
_Avoid_: History, system event, audit entry
