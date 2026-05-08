import XCTest
@testable import Haulio

@MainActor
final class TileConfigurationTests: XCTestCase {
    private var config: TileConfiguration!

    override func setUp() {
        super.setUp()
        config = TileConfiguration()
    }

    override func tearDown() {
        config = nil
        super.tearDown()
    }

    func testTileServerBaseURLIsValid() {
        XCTAssertEqual(config.tileServerBaseURL.scheme, "https")
        XCTAssertEqual(config.tileServerBaseURL.host, "tiles.haulio.app")
    }

    func testDefaultCenterIsUSACenter() {
        XCTAssertEqual(config.defaultCenter.latitude, 39.8283, accuracy: 0.0001)
        XCTAssertEqual(config.defaultCenter.longitude, -98.5795, accuracy: 0.0001)
    }

    func testDefaultZoomLevel() {
        XCTAssertEqual(config.defaultZoom, 4.0)
    }

    func testAttributionIsPresent() {
        XCTAssertFalse(config.attribution.isEmpty)
        XCTAssertTrue(config.attribution.contains("OpenStreetMap"))
    }

    func testTileURLTemplateContainsPlaceholders() {
        let template = config.tileURLTemplate
        XCTAssertTrue(template.contains("{z}"))
        XCTAssertTrue(template.contains("{x}"))
        XCTAssertTrue(template.contains("{y}"))
        XCTAssertTrue(template.contains("tiles.haulio.app"))
    }
}
