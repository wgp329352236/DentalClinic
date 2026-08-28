import { type Locator, type Page } from '@playwright/test';
import { test as authTest, expect, API_BASE_URL } from './auth.fixture';

/**
 * Base URL for the frontend dev server.
 */
export const BASE_URL = 'http://localhost:5666';

/**
 * API URL for direct backend requests.
 */
export { API_BASE_URL };

/**
 * Ant Design Vue component selectors used throughout E2E tests.
 */
export const Selectors = {
  table: '.ant-table',
  tableBody: '.ant-table-tbody',
  tableRow: '.ant-table-row',
  tableCell: '.ant-table-cell',
  tableLoading: '.ant-table-loading',
  tablePagination: '.ant-pagination',
  tableEmpty: '.ant-empty',

  modal: '.ant-modal',
  modalContent: '.ant-modal-content',
  modalHeader: '.ant-modal-header',
  modalBody: '.ant-modal-body',
  modalFooter: '.ant-modal-footer',
  modalClose: '.ant-modal-close',
  modalMask: '.ant-modal-mask',
  modalTitle: '.ant-modal-title',

  form: '.ant-form',
  formItem: '.ant-form-item',
  formItemError: '.ant-form-item-explain-error',

  button: '.ant-btn',
  buttonPrimary: '.ant-btn-primary',
  buttonDanger: '.ant-btn-dangerous',

  spin: '.ant-spin',
  spinSpinning: '.ant-spin-spinning',

  message: '.ant-message',
  messageSuccess: '.ant-message-success',
  messageError: '.ant-message-error',

  tag: '.ant-tag',
  tabs: '.ant-tabs',
  tabPane: '.ant-tabs-tabpane',
  tabNav: '.ant-tabs-nav',

  input: '.ant-input',
  select: '.ant-select',
  switch: '.ant-switch',
  inputNumber: '.ant-input-number',
} as const;

/**
 * Helper methods for common E2E test operations.
 * These are designed to work with Ant Design Vue components.
 */
export class PlaywrightBase {
  constructor(public readonly page: Page) {}

  /**
   * Navigate to a specific route path within the application.
   * Waits for the page to be fully loaded (network idle).
   *
   * @param path - Route path (e.g., '/game/list', '/system/user')
   */
  async navigateTo(path: string): Promise<void> {
    const url = path.startsWith('http') ? path : `${BASE_URL}${path}`;
    await this.page.goto(url, { waitUntil: 'networkidle' });
  }

  /**
   * Wait for the Ant Design table to finish loading.
   * Waits for the loading spinner to disappear and table rows to be present.
   *
   * @param timeout - Maximum wait time in milliseconds (default: 15000)
   */
  async waitForTableLoad(timeout: number = 15000): Promise<void> {
    // Wait for any spinning indicator to disappear
    await this.page
      .locator(Selectors.spinSpinning)
      .waitFor({ state: 'hidden', timeout })
      .catch(() => {
        // Spinner may not appear if data loads quickly
      });

    // Wait for the table body to be visible
    await this.page
      .locator(Selectors.tableBody)
      .waitFor({ state: 'visible', timeout });
  }

  /**
   * Get all visible table rows from the Ant Design table.
   *
   * @returns Array of Locator objects for each table row
   */
  async getTableRows(): Promise<Locator[]> {
    await this.waitForTableLoad();
    const rows = this.page.locator(Selectors.tableRow);
    const count = await rows.count();
    const result: Locator[] = [];
    for (let i = 0; i < count; i++) {
      result.push(rows.nth(i));
    }
    return result;
  }

  /**
   * Open a modal by clicking a button with the specified text.
   * Waits for the modal to be fully visible.
   *
   * @param buttonText - The text content of the button to click
   * @param timeout - Maximum wait time for modal to appear (default: 10000)
   */
  async openModal(buttonText: string, timeout: number = 10000): Promise<Locator> {
    // Click the button that triggers the modal
    await this.page.getByRole('button', { name: buttonText }).click();

    // Wait for the modal to appear
    const modal = this.page.locator(Selectors.modal).last();
    await modal.waitFor({ state: 'visible', timeout });

    return modal;
  }

  /**
   * Close the currently visible modal.
   * Tries the close button first, then falls back to clicking the mask.
   */
  async closeModal(): Promise<void> {
    const modal = this.page.locator(Selectors.modal).last();

    // Try clicking the close button
    const closeBtn = modal.locator(Selectors.modalClose);
    if (await closeBtn.isVisible()) {
      await closeBtn.click();
    } else {
      // Fall back to pressing Escape
      await this.page.keyboard.press('Escape');
    }

    // Wait for modal to disappear
    await modal.waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {
      // Modal may already be hidden
    });
  }

  /**
   * Submit the form inside the currently visible modal by clicking
   * the primary button in the modal footer (typically "确定" or "提交").
   * If no modal is visible, submits the form on the page.
   */
  async submitForm(): Promise<void> {
    const modal = this.page.locator(Selectors.modal).last();
    const isModalVisible = await modal.isVisible().catch(() => false);

    if (isModalVisible) {
      // Click the primary button in the modal footer
      const footer = modal.locator(Selectors.modalFooter);
      const submitBtn = footer.locator(Selectors.buttonPrimary).first();
      if (await submitBtn.isVisible()) {
        await submitBtn.click();
      } else {
        // Try any button with common submit text
        const okBtn = footer.getByRole('button', { name: /确定|提交|保存|OK|Submit/i });
        await okBtn.click();
      }
    } else {
      // Submit the form on the page directly
      const submitBtn = this.page.locator(`${Selectors.form} ${Selectors.buttonPrimary}`).first();
      await submitBtn.click();
    }
  }

  /**
   * Wait for a success message to appear (Ant Design message component).
   *
   * @param timeout - Maximum wait time (default: 5000)
   */
  async waitForSuccessMessage(timeout: number = 5000): Promise<void> {
    await this.page
      .locator(Selectors.messageSuccess)
      .waitFor({ state: 'visible', timeout });
  }

  /**
   * Wait for an error message to appear (Ant Design message component).
   *
   * @param timeout - Maximum wait time (default: 5000)
   */
  async waitForErrorMessage(timeout: number = 5000): Promise<void> {
    await this.page
      .locator(Selectors.messageError)
      .waitFor({ state: 'visible', timeout });
  }

  /**
   * Get the text content of a specific table cell by row and column index.
   *
   * @param rowIndex - Zero-based row index
   * @param colIndex - Zero-based column index
   */
  async getTableCellText(rowIndex: number, colIndex: number): Promise<string> {
    const row = this.page.locator(Selectors.tableRow).nth(rowIndex);
    const cell = row.locator(Selectors.tableCell).nth(colIndex);
    return (await cell.textContent()) ?? '';
  }

  /**
   * Check if the table is showing the empty state.
   */
  async isTableEmpty(): Promise<boolean> {
    const empty = this.page.locator(Selectors.tableEmpty);
    return empty.isVisible();
  }

  /**
   * Check if a loading spinner is currently visible.
   */
  async isLoading(): Promise<boolean> {
    const spinner = this.page.locator(Selectors.spinSpinning);
    return spinner.isVisible();
  }

  /**
   * Wait for navigation to complete after an action.
   */
  async waitForNavigation(timeout: number = 10000): Promise<void> {
    await this.page.waitForLoadState('networkidle', { timeout });
  }

  /**
   * Fill a form field by its label text.
   *
   * @param label - The label text of the form field
   * @param value - The value to fill in
   */
  async fillFormField(label: string, value: string): Promise<void> {
    const formItem = this.page.locator(Selectors.formItem).filter({ hasText: label });
    const input = formItem.locator(`${Selectors.input}, textarea`).first();
    await input.fill(value);
  }

  /**
   * Click a button within a specific table row (action column).
   *
   * @param rowIndex - Zero-based row index
   * @param buttonText - Text of the action button to click
   */
  async clickRowAction(rowIndex: number, buttonText: string): Promise<void> {
    const row = this.page.locator(Selectors.tableRow).nth(rowIndex);
    await row.getByRole('button', { name: buttonText }).click();
  }

  /**
   * Confirm a Popconfirm or Modal.confirm dialog by clicking the OK/confirm button.
   */
  async confirmDialog(): Promise<void> {
    // Ant Design Popconfirm uses .ant-popconfirm, Modal.confirm uses .ant-modal-confirm
    const confirmBtn = this.page
      .locator('.ant-popconfirm .ant-btn-primary, .ant-modal-confirm .ant-btn-primary')
      .first();
    await confirmBtn.waitFor({ state: 'visible', timeout: 5000 });
    await confirmBtn.click();
  }

  /**
   * Cancel a Popconfirm or Modal.confirm dialog.
   */
  async cancelDialog(): Promise<void> {
    const cancelBtn = this.page
      .locator('.ant-popconfirm .ant-btn:not(.ant-btn-primary), .ant-modal-confirm .ant-btn:not(.ant-btn-primary)')
      .first();
    await cancelBtn.waitFor({ state: 'visible', timeout: 5000 });
    await cancelBtn.click();
  }
}

/**
 * Extended test fixture that provides both authentication and PlaywrightBase helpers.
 */
export const test = authTest.extend<{ base: PlaywrightBase }>({
  base: async ({ authenticatedPage }, use) => {
    const helper = new PlaywrightBase(authenticatedPage);
    await use(helper);
  },
});

export { expect } from '@playwright/test';
