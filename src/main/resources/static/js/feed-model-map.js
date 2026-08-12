(function () {
    "use strict";

    const mapElement = document.getElementById("farmDistributionMap");
    const pinsElement = document.getElementById("farmDistributionPins");
    if (!mapElement || !pinsElement) return;

    const escapeHtml = function (value) {
        return String(value || "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    };

    const markerColor = function (animalType) {
        if (animalType.includes("돼지")) return "#e9a72e";
        if (animalType.includes("소")) return "#24905a";
        return "#397bc5";
    };

    const farms = Array.from(pinsElement.querySelectorAll("span")).map(function (pin) {
        return {
            name: pin.dataset.name || "농장",
            animalType: pin.dataset.animal || "축종 미지정",
            warehouseName: pin.dataset.warehouse || "담당 창고 미지정",
            latitude: Number(pin.dataset.lat),
            longitude: Number(pin.dataset.lng),
            xPercent: Number(pin.dataset.x || 50),
            yPercent: Number(pin.dataset.y || 50)
        };
    }).filter(function (farm) {
        return Number.isFinite(farm.latitude) && Number.isFinite(farm.longitude);
    });

    const renderFallback = function () {
        mapElement.classList.add("wms-network-fallback", "ff-farm-map-fallback");
        farms.forEach(function (farm) {
            const marker = document.createElement("button");
            marker.type = "button";
            marker.className = "ff-farm-fallback-pin";
            marker.style.left = farm.xPercent + "%";
            marker.style.top = farm.yPercent + "%";
            marker.style.backgroundColor = markerColor(farm.animalType);
            marker.textContent = farm.name;
            marker.title = farm.animalType + " · " + farm.warehouseName;
            mapElement.appendChild(marker);
        });
    };

    const initializeLeaflet = function () {
        if (!window.L) {
            renderFallback();
            return;
        }

        const nationwideCenter = [36.25, 127.7];
        const map = window.L.map(mapElement, {
            scrollWheelZoom: false
        }).setView(nationwideCenter, 7);

        window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            maxZoom: 18,
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);

        farms.forEach(function (farm) {
            const color = markerColor(farm.animalType);
            window.L.circleMarker([farm.latitude, farm.longitude], {
                radius: 9,
                color: "#ffffff",
                weight: 3,
                fillColor: color,
                fillOpacity: 1
            }).addTo(map).bindPopup(
                '<div class="ff-farm-map-popup">' +
                "<strong>" + escapeHtml(farm.name) + "</strong>" +
                "<span>축종 <b>" + escapeHtml(farm.animalType) + "</b></span>" +
                "<span>담당 창고 <b>" + escapeHtml(farm.warehouseName) + "</b></span>" +
                "</div>"
            );
        });

        const resetButton = document.getElementById("farmDistributionReset");
        if (resetButton) {
            resetButton.addEventListener("click", function () {
                map.setView(nationwideCenter, 7);
            });
        }

        window.setTimeout(function () { map.invalidateSize(); }, 120);
    };

    if (window.L) {
        initializeLeaflet();
        return;
    }

    if (!document.querySelector('link[data-feed-model-leaflet]')) {
        const stylesheet = document.createElement("link");
        stylesheet.rel = "stylesheet";
        stylesheet.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
        stylesheet.dataset.feedModelLeaflet = "true";
        document.head.appendChild(stylesheet);
    }

    const existingScript = document.querySelector('script[data-feed-model-leaflet]');
    if (existingScript) {
        existingScript.addEventListener("load", initializeLeaflet, { once: true });
        existingScript.addEventListener("error", renderFallback, { once: true });
        return;
    }

    const script = document.createElement("script");
    script.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
    script.dataset.feedModelLeaflet = "true";
    script.addEventListener("load", initializeLeaflet, { once: true });
    script.addEventListener("error", renderFallback, { once: true });
    document.head.appendChild(script);
})();
