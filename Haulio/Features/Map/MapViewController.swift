import SwiftUI
import UIKit
// MapLibre GL Native iOS — SPM: https://github.com/maplibre/maplibre-gl-native-distribution
import MapLibre

/// UIViewRepresentable wrapping MLNMapView for SwiftUI integration.
@MainActor
struct MapViewController: UIViewRepresentable {
    let viewModel: MapViewModel

    func makeUIView(context: Context) -> MLNMapView {
        let mapView = MLNMapView(frame: .zero)

        // Configure style
        if let styleURL = viewModel.styleURL {
            mapView.styleURL = styleURL
        }

        // Default center and zoom
        mapView.setCenter(
            viewModel.mapCenter,
            zoomLevel: viewModel.zoomLevel,
            animated: false
        )

        // User location display (animated blue pulsing dot)
        mapView.showsUserLocation = true
        mapView.userTrackingMode = .none

        // Attribution (bottom-left corner — OSM requirement)
        mapView.attributionButtonPosition = .bottomLeft
        mapView.logoView.isHidden = true

        // Delegate
        mapView.delegate = context.coordinator

        return mapView
    }

    func updateUIView(_ mapView: MLNMapView, context: Context) {
        // Update map center if the view model center changed significantly
        let currentCenter = mapView.centerCoordinate
        let targetCenter = viewModel.mapCenter
        let threshold = 0.0001

        if abs(currentCenter.latitude - targetCenter.latitude) > threshold ||
           abs(currentCenter.longitude - targetCenter.longitude) > threshold {
            mapView.setCenter(targetCenter, animated: true)
        }
    }

    func makeCoordinator() -> MapCoordinator {
        MapCoordinator(viewModel: viewModel)
    }

    // MARK: - Coordinator

    final class MapCoordinator: NSObject, MLNMapViewDelegate {
        let viewModel: MapViewModel

        init(viewModel: MapViewModel) {
            self.viewModel = viewModel
        }

        /// Customize user location annotation to show animated blue pulsing dot.
        func mapView(_ mapView: MLNMapView, viewFor annotation: MLNAnnotation) -> MLNAnnotationView? {
            guard annotation is MLNUserLocation else { return nil }

            let identifier = "UserLocationPulse"
            var annotationView = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)

            if annotationView == nil {
                annotationView = PulsingDotAnnotationView(
                    annotation: annotation,
                    reuseIdentifier: identifier
                )
            }

            return annotationView
        }

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            // Style loaded successfully
        }
    }
}

// MARK: - Pulsing Dot Annotation View

/// Custom annotation view showing an animated blue pulsing dot for user location.
private final class PulsingDotAnnotationView: MLNAnnotationView {
    private let dotSize: CGFloat = 20
    private let pulseSize: CGFloat = 40

    override init(annotation: MLNAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        setupView()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    private func setupView() {
        frame = CGRect(x: 0, y: 0, width: pulseSize, height: pulseSize)

        // Pulse layer (outer ring animation)
        let pulseLayer = CALayer()
        pulseLayer.frame = bounds
        pulseLayer.cornerRadius = pulseSize / 2
        pulseLayer.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.2).cgColor
        layer.addSublayer(pulseLayer)

        // Animate pulse
        let pulseAnimation = CABasicAnimation(keyPath: "transform.scale")
        pulseAnimation.fromValue = 0.8
        pulseAnimation.toValue = 1.4
        pulseAnimation.duration = 1.5
        pulseAnimation.autoreverses = true
        pulseAnimation.repeatCount = .infinity
        pulseAnimation.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        pulseLayer.add(pulseAnimation, forKey: "pulse")

        let opacityAnimation = CABasicAnimation(keyPath: "opacity")
        opacityAnimation.fromValue = 0.8
        opacityAnimation.toValue = 0.3
        opacityAnimation.duration = 1.5
        opacityAnimation.autoreverses = true
        opacityAnimation.repeatCount = .infinity
        opacityAnimation.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
        pulseLayer.add(opacityAnimation, forKey: "opacity")

        // Center dot (solid blue)
        let dotLayer = CALayer()
        let dotOrigin = (pulseSize - dotSize) / 2
        dotLayer.frame = CGRect(x: dotOrigin, y: dotOrigin, width: dotSize, height: dotSize)
        dotLayer.cornerRadius = dotSize / 2
        dotLayer.backgroundColor = UIColor.systemBlue.cgColor
        dotLayer.borderColor = UIColor.white.cgColor
        dotLayer.borderWidth = 3
        layer.addSublayer(dotLayer)
    }
}
