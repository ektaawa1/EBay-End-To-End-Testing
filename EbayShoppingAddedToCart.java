package week16;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.xssf.usermodel.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.*;
import org.testng.Assert;
import org.testng.annotations.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;

public class EbayShoppingAddedToCart {
    Logger logger = LogManager.getLogger(EbayShoppingAddedToCart.class);
    WebDriver driver;
    WebDriverWait wait;

    String driverType = "webdriver.gecko.driver";
    String driverPath = "C:\\BrowserDrivers\\geckodriver-v0.33.0\\geckodriver.exe";
    String xlsxFilePath = "C:\\Users\\ekta9\\Downloads\\SFBU_Spring2025\\Testing\\Week 16\\CS522_2025_MyProject_150337_EktaAwasthi\\ShoppingFile2.xlsx";
    String screenshotPath = "C:\\Users\\ekta9\\Downloads\\SFBU_Spring2025\\Testing\\Week 16\\CS522_2025_MyProject_150337_EktaAwasthi\\Screenshots";
    String sheetName = "Sheet1";
    String url = "https://www.ebay.com";

    @BeforeTest
    public void setup() {
        System.setProperty(driverType, driverPath);

        FirefoxOptions options = new FirefoxOptions();
        options.addPreference("dom.popup_allowed_events", "change click dblclick keydown keyup mousedown mouseup");
        options.addPreference("notifications.enabled", false); // Disable notifications

        driver = new FirefoxDriver(options);
        driver.manage().window().maximize();
        driver.manage().deleteAllCookies();
        driver.get(url);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        logger.info("Launched eBay website.");
    }

    @Test
    public void ebayShoppingTest() throws IOException {
        FileInputStream file = new FileInputStream(xlsxFilePath);
        XSSFWorkbook workbook = new XSSFWorkbook(file);
        XSSFSheet sheet = workbook.getSheet(sheetName);
        int noOfRows = sheet.getLastRowNum();
        logger.info("Excel rows found: " + noOfRows);

        for (int row = 1; row <= noOfRows; row++) {
            XSSFRow currentRow = sheet.getRow(row);
            String productName = currentRow.getCell(0).getStringCellValue();
            String expectedPrice = currentRow.getCell(1).getStringCellValue();

            logger.info("Searching for product: " + productName);
            searchProduct(productName);
            closeGooglePopups();

            try {
                takeSnapShot(driver, screenshotPath + "\\search_" + sanitizeFileName(productName) + ".png");
            } catch (Exception e) {
                logger.error("Screenshot failed for product search: " + productName, e);
            }

            if (selectFirstValidProduct()) {
                try {
                    takeSnapShot(driver, screenshotPath + "\\product_" + sanitizeFileName(productName) + ".png");

                    WebElement productTitleElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1.x-item-title__mainTitle span")));
                    String actualProductName = productTitleElement.getText().trim();

                    WebElement priceElement = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.x-price-primary span.ux-textspans")));
                    String actualPrice = priceElement.getText().trim().replaceAll("[^\\d.]", "");

                    logger.info("Actual Product Name: " + actualProductName);
                    logger.info("Actual Product Price: " + actualPrice);

                    addToCart(driver);
                    handleAddedToCartDialog(driver, actualProductName, actualPrice);

                    takeSnapShot(driver, screenshotPath + "\\cart_" + sanitizeFileName(productName) + ".png");
                } catch (Exception e) {
                    logger.error("Error during product handling: " + productName, e);
                }
            }
        }
        workbook.close();
    }

    private String sanitizeFileName(String product) {
        return product.replaceAll("[^a-zA-Z0-9\\-_.]", "_");
    }

    public void searchProduct(String product) {
        WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("gh-ac")));
        searchBox.clear();
        searchBox.sendKeys(product);
        searchBox.sendKeys(Keys.RETURN);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("ul.srp-results")));
    }

    public void closeGooglePopups() {
        try {
            WebElement acceptCookiesButton = driver.findElement(By.xpath("//button[contains(text(), 'Accept') or contains(text(), 'I agree')]"));
            if (acceptCookiesButton.isDisplayed()) {
                acceptCookiesButton.click();
                logger.info("Accepted cookie consent pop-up.");
            }
        } catch (NoSuchElementException e) {
            logger.info("No cookie consent pop-up found.");
        }

        try {
            WebElement cancelSignInButton = driver.findElement(By.xpath("//button[contains(text(), 'Cancel') or contains(text(), 'Not now')]"));
            if (cancelSignInButton.isDisplayed()) {
                cancelSignInButton.click();
                logger.info("Closed Google sign-in pop-up.");
            }
        } catch (NoSuchElementException e) {
            logger.info("No Google sign-in pop-up found.");
        }

        try {
            WebElement signInButton = driver.findElement(By.xpath("//button[contains(text(), 'Sign in')]"));
            if (signInButton.isDisplayed()) {
                signInButton.click();
                logger.info("Sign-in prompt appeared and dismissed.");
            }
        } catch (NoSuchElementException e) {
            logger.info("No 'Sign in' prompt found.");
        }
    }


    public boolean selectFirstValidProduct() {
        try {
            List<WebElement> results = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("ul.srp-results > li.s-item")));
            if (results.isEmpty()) return false;

            String originalWindow = driver.getWindowHandle();

            for (WebElement item : results) {
                if (item.getText().contains("Buy It Now")) {
                    WebElement link = item.findElement(By.cssSelector("a.s-item__link"));
                    link.click();

                    // Handle possible new tab or window
                    for (String windowHandle : driver.getWindowHandles()) {
                        if (!windowHandle.equals(originalWindow)) {
                            driver.switchTo().window(windowHandle);
                            logger.info("Switched to new product tab/window.");
                            break;
                        }
                    }

                    // Wait for any valid element on product page
                    wait.until(ExpectedConditions.or(
                            ExpectedConditions.visibilityOfElementLocated(By.cssSelector("h1.x-item-title__mainTitle")),
                            ExpectedConditions.presenceOfElementLocated(By.id("atcBtn_btn_1"))
                    ));

                    logger.info("Product page loaded and required elements found.");
                    return true;
                }
            }

            logger.warn("No 'Buy It Now' listings found.");
            return false;
        } catch (Exception e) {
            logger.error("Failed to open or detect product page properly.", e);
            return false;
        }
    }

    public void addToCart(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        try {
            List<WebElement> overlays = driver.findElements(By.cssSelector(".x-atc-action.overlay-placeholder.loading"));
            if (!overlays.isEmpty()) {
                logger.info("Overlay is present, waiting for it to disappear...");
                wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".x-atc-action.overlay-placeholder.loading")));
            }

            WebElement addToCartBtn = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#atcBtn_btn_1")));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", addToCartBtn);
            addToCartBtn.click();
            logger.info("Clicked on Add to Cart button successfully.");

        } catch (TimeoutException e) {
            logger.error("Timed out waiting for Add to Cart button.", e);
        } catch (ElementClickInterceptedException e) {
            logger.warn("Element was not clickable, retrying...");
            try {
                Thread.sleep(2000);
                WebElement retryBtn = driver.findElement(By.cssSelector("#atcBtn_btn_1"));
                retryBtn.click();
                logger.info("Retry click on Add to Cart succeeded.");
            } catch (Exception retryEx) {
                logger.error("Retry failed.", retryEx);
            }
        } catch (Exception e) {
            logger.error("Unexpected error adding to cart.", e);
        }
    }


    public void handleAddedToCartDialog(WebDriver driver, String expectedProductName, String expectedPrice) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            // Step 1: Wait for dialog overlay
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("div.lightbox-dialog")));

            // Step 2: Try to find the close button directly
            List<WebElement> closeButtons = driver.findElements(By.cssSelector("button.lightbox-dialog__close"));

            if (!closeButtons.isEmpty()) {
                WebElement closeBtn = closeButtons.get(0);

                // Step 3: Try clicking with JavaScript (bypass Selenium click blocking)
                js.executeScript("arguments[0].click();", closeBtn);
                System.out.println("Dialog closed using JS click.");
            } else {
                System.out.println("Close button not found.");
            }

            // Step 4: Wait for the dialog to go away
            // Wait until the overlay is either not visible or not in DOM anymore
            WebDriverWait overlayWait = new WebDriverWait(driver, Duration.ofSeconds(15));
            overlayWait.until(ExpectedConditions.invisibilityOfElementLocated(
                    By.cssSelector("div.lightbox-dialog.ux-overlay.x-atc-action__overlay"))); // or "div.lightbox-dialog.ux-overlay"
        } catch (TimeoutException e) {
            System.out.println("Dialog or close button did not appear in time. Skipping dialog handling.");
        } catch (Exception e) {
            System.err.println("Unexpected error while handling cart overlay: " + e.getMessage());
        }

        // Step 5: Proceed to click "See in cart"
        try {
            WebDriverWait cartWait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement seeInCartBtn = cartWait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[text()='See in cart']/ancestor::a")));

            try {
                seeInCartBtn.click();
                System.out.println("Clicked 'See in Cart' successfully.");
            } catch (ElementClickInterceptedException ex) {
                js.executeScript("arguments[0].click();", seeInCartBtn);
                System.out.println("Clicked 'See in Cart' using JS fallback.");
            }
        } catch (Exception e) {
            System.err.println("Failed to click 'See in cart': " + e.getMessage());
        }

        // Step 6: Validate product details in the cart
        try {
            WebDriverWait cartPageWait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Wait until cart item is visible
            WebElement cartItemName = cartPageWait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("div.item-details_card--title span.ux-textspans")));
            WebElement cartItemPrice = cartPageWait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//div[@data-testid='ux-labels-values']//span[contains(text(), '$')]")));

            String actualCartName = cartItemName.getText().trim();
            String actualCartPrice = cartItemPrice.getText().trim().replaceAll("[^\\d.]", "");

            System.out.println("🛒 Cart Product Name: " + actualCartName);
            System.out.println("🛒 Cart Product Price: " + actualCartPrice);

            // Soft match for name (in case cart version is abbreviated), exact for price
            if (actualCartName.toLowerCase().contains(expectedProductName.toLowerCase()) &&
                    actualCartPrice.equals(expectedPrice)) {
                System.out.println("✅ Product name and price match in cart.");
            } else {
                System.err.println("❌ Cart mismatch: Expected '" + expectedProductName + "' & $" + expectedPrice +
                        " but found '" + actualCartName + "' & $" + actualCartPrice);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to verify product details in cart: " + e.getMessage());
        }


    }


    public void takeSnapShot(WebDriver webdriver, String fileWithPath) throws IOException {
        TakesScreenshot scrShot = ((TakesScreenshot) webdriver);
        File srcFile = scrShot.getScreenshotAs(OutputType.FILE);
        File destFile = new File(fileWithPath);
        FileUtils.copyFile(srcFile, destFile);
    }

    @AfterTest
    public void tearDown() {
        driver.quit();
        logger.info("Browser closed.");
    }
}
