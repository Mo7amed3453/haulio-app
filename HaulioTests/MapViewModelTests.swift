import XCTest
import CoreLocation
@testable import Haulio

@MainActor
final class MapViewModelTests: XCTestCase {
    private var viewModel: MapViewModel!

    override func setUp() {
        super.setUp()
        let locationService = LocationService()
        let styleProvider = MapStyleProvider()
        viewModel = MapViewModel(
            locationService: locationService,
            styleProvider: styleProvider
        )
    }

    override func tearDown() {
        viewModel = nil
        super.tearDown()
    }

    func testInitialState() {
        XCTAssertNil(viewModel.userLocation)
        XCTAssertFalse(viewModel.isTracking)
        XCTAssertEqual(viewModel.searchText, "")
        XCTAssertEqual(viewModel.zoomLevel, 4.0)
    }

    func testInitialMapCenterIsUSACenter() {
        let center = viewModel.mapCenter
        XCTAssertEqual(center.latitude, 39.8283, accuracy: 0.0001)
        XCTAssertEqual(center.longitude, -98.5795, accuracy: 0.0001)
    }

    func testSearchTextBinding() {
        viewModel.searchText = "Test destination"
        XCTAssertEqual(viewModel.searchText, "Test destination")
    }

    func testStopLocationTrackingResetsState() {
        viewModel.stopLocationTracking()
        XCTAssertFalse(viewModel.isTracking)
    }

    func testZoomLevelDefault() {
        XCTAssertEqual(viewModel.zoomLevel, TileConfiguration().defaultZoom)
    }
}
