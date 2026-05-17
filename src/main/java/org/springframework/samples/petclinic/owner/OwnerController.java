/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import jakarta.validation.Valid;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spring MVC controller responsible for handling all HTTP requests related to
 * {@link Owner} management. Exposes endpoints for creating, searching, viewing,
 * and updating owners, with paginated list support.
 *
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Wick Dynex
 */
@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	/** Number of owner records displayed per page in search results. */
	private static final int PAGE_SIZE = 5;

	private final OwnerRepository owners;

	public OwnerController(OwnerRepository owners) {
		this.owners = owners;
	}

	/**
	 * Prevents the {@code id} field from being set via form binding, guarding
	 * against mass-assignment vulnerabilities.
	 * @param dataBinder the data binder to configure
	 */
	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id", "*.id");
	}

	/**
	 * Resolves the {@link Owner} model attribute before each request. Returns a
	 * new {@link Owner} when no {@code ownerId} path variable is present (i.e.
	 * during creation), or loads the existing owner from the repository.
	 * @param ownerId the owner's primary key, or {@code null} for new-owner flows
	 * @return the existing {@link Owner} or a new empty instance
	 * @throws IllegalArgumentException if an {@code ownerId} is supplied but no
	 * matching owner exists in the database
	 */
	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		return ownerId == null ? new Owner()
				: this.owners.findById(ownerId)
					.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId
							+ ". Please ensure the ID is correct " + "and the owner exists in the database."));
	}

	/**
	 * Displays the form for creating a new owner.
	 * @return the logical view name for the create/update owner form
	 */
	@GetMapping("/owners/new")
	public String initCreationForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes the new-owner form submission. Saves the owner if validation
	 * passes, or returns the form view with error messages if it fails.
	 * @param owner the owner instance populated from the form
	 * @param result holds any validation errors produced by bean validation
	 * @param redirectAttributes used to pass a success or error flash message
	 * after redirect
	 * @return a redirect to the new owner's detail page on success, or the form
	 * view on validation failure
	 */
	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in creating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "New Owner Created");
		return "redirect:/owners/" + owner.getId();
	}

	/**
	 * Displays the owner search form.
	 * @return the logical view name for the find-owners page
	 */
	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	/**
	 * Processes the owner search form. Searches by last name and handles three
	 * outcomes: no results found (returns the search form with an error), exactly
	 * one result (redirects directly to that owner's detail page), or multiple
	 * results (displays a paginated list).
	 * @param page the 1-based page number to display, defaults to 1
	 * @param owner used to carry the {@code lastName} search parameter from the form
	 * @param result holds any field-level errors (e.g. no owner found)
	 * @param model the Spring MVC model used to pass pagination data to the view
	 * @return the owners list view, a redirect to a single owner, or the search
	 * form on no results
	 */
	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {
		// allow parameterless GET request for /owners to return all records
		String lastName = owner.getLastName();
		if (lastName == null) {
			lastName = ""; // empty string signifies broadest possible search
		}

		// find owners by last name
		Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, lastName);
		if (ownersResults.isEmpty()) {
			// no owners found
			result.rejectValue("lastName", "notFound", "not found");
			return "owners/findOwners";
		}

		if (ownersResults.getTotalElements() == 1) {
			// 1 owner found
			owner = ownersResults.iterator().next();
			return "redirect:/owners/" + owner.getId();
		}

		// multiple owners found
		return addPaginationModel(page, model, ownersResults);
	}

	/**
	 * Populates the model with pagination metadata and the current page of owners,
	 * then returns the owners list view.
	 * @param page the current 1-based page number
	 * @param model the Spring MVC model to populate
	 * @param paginated the paginated query result
	 * @return the logical view name for the owners list page
	 */
	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	/**
	 * Retrieves a single page of owners whose last name starts with the given
	 * string.
	 * @param page the 1-based page number requested
	 * @param lastname the last-name prefix to filter by
	 * @return a {@link Page} of matching {@link Owner} instances
	 */
	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		Pageable pageable = PageRequest.of(page - 1, PAGE_SIZE);
		return owners.findByLastNameStartingWith(lastname, pageable);
	}

	/**
	 * Displays the form for editing an existing owner. The owner is pre-populated
	 * via the {@link #findOwner} model attribute method.
	 * @return the logical view name for the create/update owner form
	 */
	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	/**
	 * Processes the owner update form submission. Validates the submitted data,
	 * checks that the form owner ID matches the URL path variable to prevent
	 * cross-owner edits, and saves on success.
	 * @param owner the owner instance populated from the submitted form
	 * @param result holds any validation errors produced by bean validation
	 * @param ownerId the owner's primary key taken from the URL path
	 * @param redirectAttributes used to pass a success or error flash message
	 * after redirect
	 * @return a redirect to the owner's detail page on success, or the form view
	 * on validation failure or ID mismatch
	 */
	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
			RedirectAttributes redirectAttributes) {
		if (result.hasErrors()) {
			redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		if (!Objects.equals(owner.getId(), ownerId)) {
			result.rejectValue("id", "mismatch", "The owner ID in the form does not match the URL.");
			redirectAttributes.addFlashAttribute("error", "Owner ID mismatch. Please try again.");
			return "redirect:/owners/{ownerId}/edit";
		}

		owner.setId(ownerId);
		this.owners.save(owner);
		redirectAttributes.addFlashAttribute("message", "Owner Values Updated");
		return "redirect:/owners/{ownerId}";
	}

	/**
	 * Custom handler for displaying an owner.
	 * @param ownerId the ID of the owner to display
	 * @return a ModelMap with the model attributes for the view
	 */
	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		Optional<Owner> optionalOwner = this.owners.findById(ownerId);
		Owner owner = optionalOwner.orElseThrow(() -> new IllegalArgumentException(
				"Owner not found with id: " + ownerId + ". Please ensure the ID is correct "));
		mav.addObject(owner);
		return mav;
	}

}