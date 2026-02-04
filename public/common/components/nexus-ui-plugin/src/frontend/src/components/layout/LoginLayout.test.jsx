/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

import React from "react";
import { render, screen } from "@testing-library/react";
import { ExtJS } from "@sonatype/nexus-ui-plugin";
import LoginLayout from "./LoginLayout";

jest.mock("@sonatype/nexus-ui-plugin", () => ({
  ExtJS: {
    useState: jest.fn(),
    state: jest.fn(),
  },
}));

const mockState = {
  getEdition: jest.fn(),
  getValue: jest.fn(),
};

const defaultLogoConfig = {
  proLight: "/logos/pro-light.svg",
  proDark: "/logos/pro-dark.svg",
  ceLight: "/logos/ce-light.svg",
  ceDark: "/logos/ce-dark.svg",
  coreLight: "/logos/core-light.svg",
  coreDark: "/logos/core-dark.svg",
};

describe("LoginLayout", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    ExtJS.state.mockReturnValue(mockState);
    mockState.getEdition.mockReturnValue("PRO");
    // By default, no context path
    mockState.getValue.mockReturnValue("");
    ExtJS.useState.mockImplementation((fn) => fn());
  });

  it("render component for Professional", () => {
    const { container } = render(
        <LoginLayout logoConfig={defaultLogoConfig}>
          <div>Test Content</div>
        </LoginLayout>
    );

    // verify structure rendered
    expect(container.querySelector(".login-header")).toBeInTheDocument();
    expect(container.querySelector(".nxrm-login-page-main")).toBeInTheDocument();
    expect(screen.getByText("Test Content")).toBeInTheDocument();

    // verify context path is / by default
    expect(container.querySelector(".login-header a[href]")).toHaveAttribute("href", "/");

    // verify logo images are rendered
    const images = container
        .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Professional"]');
    expect(images).toHaveLength(2);
    expect(images[0]).toHaveAttribute("src", "/logos/pro-light.svg");
    expect(images[1]).toHaveAttribute("src", "/logos/pro-dark.svg");
    expect(mockState.getEdition).toHaveBeenCalled();
  });

  describe("logo selection", () => {
    it("falls back when PRO style is not available", () => {
      const { container } = render(
        <LoginLayout logoConfig={{}}>
          <div>Test Content</div>
        </LoginLayout>
      );

      expect(screen.getByText("Test Content")).toBeInTheDocument();
      const images = container
          .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Professional"]');
      expect(images).toHaveLength(2);
      expect(images[0]).toHaveAttribute("src", "path/to/asset.png");
      expect(images[1]).toHaveAttribute("src", "path/to/asset.png");
      expect(mockState.getEdition).toHaveBeenCalled();    });

    describe("logo selection - Community edition", () => {
      it("uses ceLight logo when available", () => {
        mockState.getEdition.mockReturnValue("COMMUNITY");

        const { container } = render(
            <LoginLayout logoConfig={defaultLogoConfig}>
              <div>Test Content</div>
            </LoginLayout>
        );

        expect(screen.getByText("Test Content")).toBeInTheDocument();
        const images = container
            .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Community"]');
        expect(images).toHaveLength(2);
        expect(images[0]).toHaveAttribute("src", "/logos/ce-light.svg");
        expect(images[1]).toHaveAttribute("src", "/logos/ce-dark.svg");
        expect(mockState.getEdition).toHaveBeenCalled();
      });

      it("falls back to proLight and proDark when Community style is not available", () => {
        mockState.getEdition.mockReturnValue("COMMUNITY");

        const logoConfigWithoutCE = {
          proLight: "/logos/pro-light.svg",
          proDark: "/logos/pro-dark.svg",
        };

        const { container } = render(
            <LoginLayout logoConfig={logoConfigWithoutCE}>
              <div>Test Content</div>
            </LoginLayout>
        );

        expect(screen.getByText("Test Content")).toBeInTheDocument();
        const images = container
            .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Community"]');
        expect(images).toHaveLength(2);
        expect(images[0]).toHaveAttribute("src", "/logos/pro-light.svg");
        expect(images[1]).toHaveAttribute("src", "/logos/pro-dark.svg");
        expect(mockState.getEdition).toHaveBeenCalled();
      });
    });

    describe("logo selection - Core edition", () => {
      beforeEach(() => {
        mockState.getEdition.mockReturnValue("distinctToCommunityAndPro");
      });

      it("uses coreLight logo when available", () => {
        const { container } = render(
            <LoginLayout logoConfig={defaultLogoConfig}>
              <div>Test Content</div>
            </LoginLayout>
        );

        expect(screen.getByText("Test Content")).toBeInTheDocument();
        const images = container
            .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Core"]');
        expect(images).toHaveLength(2);
        expect(images[0]).toHaveAttribute("src", "/logos/core-light.svg");
        expect(images[1]).toHaveAttribute("src", "/logos/core-dark.svg");
        expect(mockState.getEdition).toHaveBeenCalled();
      });

      it("falls back to proLight and proDark when Core style is not available", () => {
        const logoConfigWithoutCore = {
          proLight: "/logos/pro-light.svg",
          proDark: "/logos/pro-dark.svg",
        };

        const { container } = render(
            <LoginLayout logoConfig={logoConfigWithoutCore}>
              <div>Test Content</div>
            </LoginLayout>
        );

        expect(screen.getByText("Test Content")).toBeInTheDocument();
        const images = container
            .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Core"]');
        expect(images).toHaveLength(2);
        expect(images[0]).toHaveAttribute("src", "/logos/pro-light.svg");
        expect(images[1]).toHaveAttribute("src", "/logos/pro-dark.svg");
        expect(mockState.getEdition).toHaveBeenCalled();
      });
    });
  });

  it("uses context path from ExtJS state when provided", () => {
    mockState.getValue.mockReturnValue("/nexus-context");

    const { container } = render(
        <LoginLayout logoConfig={defaultLogoConfig}>
          <div>Test Content</div>
        </LoginLayout>
    );

    expect(screen.getByText("Test Content")).toBeInTheDocument();
    const homeLink = container.querySelector(".login-header a[href]");
    expect(homeLink).toHaveAttribute("href", "/nexus-context");
  });

  describe("edge cases", () => {
    it("handles missing logoConfig gracefully", () => {
      const { container } = render(
        <LoginLayout logoConfig={undefined}>
          <div>Test Content</div>
        </LoginLayout>
      );

      expect(screen.getByText("Test Content")).toBeInTheDocument();
      const images = container
          .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Core"]');
      expect(images).toHaveLength(0);
      expect(mockState.getEdition).toHaveBeenCalled();
    });

    it("handles empty logoConfig gracefully", () => {
      const { container } = render(
        <LoginLayout logoConfig={{}}>
          <div>Test Content</div>
        </LoginLayout>
      );

      expect(screen.getByText("Test Content")).toBeInTheDocument();
      const images = container
          .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Core"]');
      expect(images).toHaveLength(0);
      expect(mockState.getEdition).toHaveBeenCalled();
    });

    it("handles null logoConfig gracefully", () => {
      const { container } = render(
        <LoginLayout logoConfig={null}>
          <div>Test Content</div>
        </LoginLayout>
      );

      expect(screen.getByText("Test Content")).toBeInTheDocument();
      const images = container
          .querySelectorAll('.login-header img[alt="Sonatype Nexus Repository Core"]');
      expect(images).toHaveLength(0);
      expect(mockState.getEdition).toHaveBeenCalled();
    });
  });
});
